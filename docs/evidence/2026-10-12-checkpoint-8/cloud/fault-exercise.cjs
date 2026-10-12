// Checkpoint 8 on the AWS demo: inject a fault and find it through the metrics
// and the logs. The printer simulator is stopped (through SSM) while the demo
// operator starts a print for a paid order; the script then reads Prometheus
// and the backend's JSON logs on the instance, takes Grafana screenshots
// through SSM port forwarding, starts the simulator again and has the operator
// reconcile and finish the job. Run from frontend/ as the deployment user, with
// Grafana forwarded (infra/README.md, "Metrics and logs") to GRAFANA_URL:
//   GRAFANA_URL=http://localhost:13001 NODE_PATH=$PWD/node_modules node fault-exercise.cjs <paid order id> <out dir>
const { chromium } = require("@playwright/test");
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const cp7 = require("../../2026-10-12-checkpoint-7/cloud/cloud-checks.cjs");

const [orderId, OUT] = process.argv.slice(2);
const GRAFANA = process.env.GRAFANA_URL || "http://localhost:13001";
const AWS_ENV = { ...process.env, AWS_PROFILE: process.env.AWS_PROFILE || "one3d-deployer", AWS_REGION: "ap-south-1" };
const say = (line) => { cp7.log.push(line); console.log(line); };
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const time = () => new Date().toISOString().slice(11, 19);

/** Current values of a PromQL query, read on the instance (Prometheus is not published). */
async function prom(query) {
  const out = await cp7.onInstance(`docker exec one3d-prometheus-1 wget -qO- 'http://localhost:9090/api/v1/query?query=${encodeURIComponent(query)}' | python3 -c 'import json,sys
for r in json.load(sys.stdin)["data"]["result"]:
    labels = ",".join(k + "=" + v for k, v in sorted(r["metric"].items()) if k not in ("__name__", "job", "instance"))
    print("  " + labels + " " + r["value"][1])'`);
  return out || "  (no series)";
}

async function metrics() {
  for (const [label, query] of [
    ["one3d_simulator_unreachable_total", "one3d_simulator_unreachable_total"],
    ["one3d_dispatch_results_total", "one3d_dispatch_results_total"],
    ["one3d_attempts_awaiting_reconciliation", "one3d_attempts_awaiting_reconciliation"],
    ["one3d_work_items{kind=DISPATCH_START} > 0", 'one3d_work_items{kind="DISPATCH_START"} > 0'],
    ["API 5xx in the last 5 minutes", 'sum(increase(http_server_requests_seconds_count{uri=~"/api/.*",outcome="SERVER_ERROR"}[5m])) or vector(0)'],
  ]) say(`${label}:\n${await prom(query)}`);
}

function screenshot(name) {
  const password = execFileSync("aws", ["ssm", "get-parameter", "--name", "/one3d/demo/one3d-grafana-admin-password",
    "--with-decryption", "--query", "Parameter.Value", "--output", "text"], { env: AWS_ENV }).toString().trim();
  const script = path.resolve(__dirname, "../local/grafana-screenshot.cjs");
  execFileSync("node", [script, GRAFANA, path.join(OUT, name)], { env: { ...process.env, GRAFANA_PASSWORD: password } });
  say(`dashboard screenshot (through SSM port forwarding): ${name}`);
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const { context, page: o } = await cp7.signIn(browser, "operator");

  say(`\n## 1. A paid order is released (${time()} UTC)`);
  const order = await cp7.json(o, "GET", `/api/ops/orders/${orderId}`);
  say(`order ${orderId.slice(0, 8)}: ${order.status}`);
  const job = await cp7.json(o, "POST", `/api/ops/orders/${orderId}/release`);
  const printer = (await cp7.json(o, "GET", "/api/ops/printers")).find((p) => p.id === cp7.PRINTER);
  if (printer.bedCycleAttemptId) {
    await cp7.json(o, "POST", `/api/ops/printers/${cp7.PRINTER}/clear-bed`, { attemptId: printer.bedCycleAttemptId });
  }
  say(`job ${job.id.slice(0, 8)} released; printer bed clear`);
  await metrics();

  say(`\n## 2. Fault: the simulator stops (${time()} UTC); the operator starts the print`);
  say(`on the instance: ${await cp7.onInstance("docker stop one3d-simulator-1")} stopped`);
  const started = await cp7.json(o, "POST", `/api/ops/jobs/${job.id}/attempts`, { printerId: cp7.PRINTER });
  const attemptId = started.attempts.at(-1).id;
  say(`attempt ${attemptId.slice(0, 8)} recorded: ${started.attempts.at(-1).status}`);
  await sleep(45000);
  say(`after 45 s: attempt ${(await cp7.json(o, "GET", `/api/ops/jobs/${job.id}`)).attempts.at(-1).status}`);
  await sleep(20000); // Prometheus reads every 15 s
  await metrics();

  say("\n## 3. The backend's JSON log lines for that attempt (on the instance)");
  say(await cp7.onInstance(`docker logs --since 5m one3d-backend-1 2>&1 | grep '"attempt_id":"${attemptId}"' | head -6 | python3 -c 'import json,sys
for d in map(json.loads, sys.stdin):
    print("  " + json.dumps({"time": d["@timestamp"][11:19], "level": d["log"]["level"], "message": d["message"],
                             "attempt_id": d.get("attempt_id"), "command_id": d.get("command_id"), "job_id": d.get("job_id")}))'`));
  const exhausted = (await cp7.json(o, "GET", "/api/ops/work?status=EXHAUSTED")).filter((w) => w.kind === "DISPATCH_START");
  say(`start deliveries whose tries ran out (operator list): ${exhausted.length}`);
  screenshot("grafana-during-fault.png");

  say(`\n## 4. Recovery: the simulator is back (${time()} UTC); the operator reconciles`);
  say(`on the instance: ${await cp7.onInstance("docker start one3d-simulator-1")} started`);
  await sleep(15000);
  const reconciled = await cp7.json(o, "POST", `/api/ops/attempts/${attemptId}/reconcile`);
  say(`reconcile: ${reconciled.attempts ? reconciled.attempts.at(-1).status : reconciled.code}`);
  let current;
  for (let i = 0; i < 30; i++) {
    current = await cp7.json(o, "GET", `/api/ops/jobs/${job.id}`);
    if (current.attempts.at(-1).status === "RUNNING") break;
    await sleep(1000);
  }
  say(`attempt: ${current.attempts.at(-1).status}`);
  say(`simulator.sh complete: ${await cp7.onInstance("bash /opt/one3d/app/infra/deploy/simulator.sh complete")}`);
  for (let i = 0; i < 30; i++) {
    current = await cp7.json(o, "GET", `/api/ops/jobs/${job.id}`);
    if (current.status === "AWAITING_QC") break;
    await sleep(1000);
  }
  const qc = await cp7.json(o, "POST", `/api/ops/attempts/${attemptId}/qc`, { result: "PASSED", notes: "Whole-part check" });
  const ready = await cp7.json(o, "POST", `/api/ops/orders/${orderId}/ready-for-handoff`);
  const handed = await cp7.json(o, "POST", `/api/ops/orders/${orderId}/handed-over`);
  say(`job ${current.status}; QC ${qc.status}; ${ready.status}; ${handed.status}`);
  await sleep(20000);
  await metrics();
  screenshot("grafana-after-recovery.png");

  await context.close();
  await browser.close();
  fs.writeFileSync(path.join(OUT, "fault-exercise.txt"), cp7.log.join("\n") + "\n");
})().catch((e) => { console.error(e); process.exit(1); });
