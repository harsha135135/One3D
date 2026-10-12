#!/bin/bash
# Checkpoint 8 on the AWS demo: metrics and dashboards stay private, Prometheus
# reads the backend, and the stack fits the 2 GiB instance. Run from macOS as the deployment user.
set -uo pipefail
export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_REGION=ap-south-1 AWS_PAGER=""
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
SITE=https://demo.one3d.store
I=$(terraform -chdir="$ROOT/infra/terraform/demo" output -raw instance_id)
IP=$(terraform -chdir="$ROOT/infra/terraform/demo" output -raw public_ip)
on_instance() {
  local id status
  id=$(aws ssm send-command --instance-ids "$I" --document-name AWS-RunShellScript \
    --parameters "$(jq -n --arg c "$1" '{commands: [$c]}')" --query Command.CommandId --output text)
  for i in $(seq 1 40); do
    status=$(aws ssm get-command-invocation --command-id "$id" --instance-id "$I" --query Status --output text 2>/dev/null || echo Pending)
    case $status in Pending|InProgress|Delayed) sleep 2 ;; *) break ;; esac
  done
  aws ssm get-command-invocation --command-id "$id" --instance-id "$I" --query StandardOutputContent --output text
}
say() { echo; echo "## $*"; }

say "1. From the internet: only 80 and 443 accept connections (3 s timeout)"
for port in 22 80 443 3000 3001 5432 8080 8081 8090 9090 9091; do
  if nc -z -G 3 "$IP" "$port" 2> /dev/null; then echo "  $port open"; else echo "  $port closed/filtered"; fi
done

say "2. Actuator through the public site"
for path in /actuator/health /actuator/prometheus /api/actuator/health /api/actuator/prometheus /actuator/env; do
  body=$(curl -s -o - -w '\n%{http_code}' "$SITE$path")
  echo "  $path: HTTP $(tail -1 <<< "$body"), metrics in the body: $(grep -c '^one3d_' <<< "$body")"
done

say "3. On the instance: what listens where"
on_instance "ss -Hltn | awk '{print \$4}' | sort -u | grep -E ':(22|80|443|3000|3001|5432|8080|8081|8090|9090|9091)\$' | sed 's/^/  /'"

say "4. Containers, memory (t3.small, 2 GiB) and disks"
on_instance "cd /opt/one3d/app && docker compose --env-file /opt/one3d/one3d.env -f compose.yaml -f compose.deploy.yaml ps --format '  {{.Service}}: {{.Status}}' && echo && free -m | head -3 | sed 's/^/  /' && echo && docker stats --no-stream --format '  {{.Name}} {{.MemUsage}}' | sort && df -h / /opt/one3d/data | sed 's/^/  /'"

say "5. Prometheus scrapes the backend on the internal network"
on_instance "docker exec one3d-prometheus-1 wget -qO- 'http://localhost:9090/api/v1/targets' | python3 -c 'import json,sys; [print(\"  \", t[\"labels\"][\"job\"], t[\"scrapeUrl\"], t[\"health\"]) for t in json.load(sys.stdin)[\"data\"][\"activeTargets\"]]'"
on_instance "docker exec one3d-prometheus-1 wget -qO- 'http://localhost:9090/api/v1/query?query=count(%7B__name__%3D~%22one3d_.%2A%22%7D)' | python3 -c 'import json,sys; print(\"   one3d series:\", json.load(sys.stdin)[\"data\"][\"result\"][0][\"value\"][1])'"

say "6. Grafana: only on the instance's loopback, and nothing without signing in"
on_instance "for p in /api/search /api/dashboards/uid/one3d-overview /api/datasources; do echo \"  \$p without credentials: HTTP \$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3001\$p)\"; done; echo \"  provisioned dashboard and config hash label: \$(docker inspect -f '{{index .Config.Labels \"one3d.monitoring-config\"}}' one3d-grafana-1)\""
