#!/bin/bash
# Account-wide AWS Budgets for the credits shared by OneCreations and One3D
# (account level, so they are NOT destroyed with the One3D demo). Plain cost
# budgets with email alerts are free. Run as the deployment user:
#   ALERT_EMAIL=you@example.com infra/account/budgets.sh
# 1. Account-Credit-Use: monthly usage BEFORE credits (what the credits pay
#    for); alerts at 50%, 80% and 100% actual and 100% forecast.
# 2. Account-Out-Of-Pocket: monthly cost AFTER credits; alerts on any charge
#    the credits do not cover (a card is on file).
# The existing OneCreations-Monthly-Budget is left as it is.
set -euo pipefail
export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_PAGER=""
: "${ALERT_EMAIL:?set ALERT_EMAIL}"
CREDIT_USE_LIMIT=${CREDIT_USE_LIMIT:-15}
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)

budget() { # budget <name> <limit> <include credits: true|false>
  jq -n --arg name "$1" --arg limit "$2" --argjson credits "$3" '{
    BudgetName: $name, BudgetType: "COST", TimeUnit: "MONTHLY",
    BudgetLimit: {Amount: $limit, Unit: "USD"},
    CostTypes: {IncludeCredit: $credits, IncludeRefund: false, IncludeTax: true, IncludeSubscription: true,
                IncludeSupport: true, IncludeOtherSubscription: true, IncludeRecurring: true,
                IncludeUpfront: true, IncludeDiscount: true, UseBlended: false, UseAmortized: false}}'
}
alert() { # alert <ACTUAL|FORECASTED> <percent>
  jq -n --arg type "$1" --argjson pct "$2" --arg email "$ALERT_EMAIL" '{
    Notification: {NotificationType: $type, ComparisonOperator: "GREATER_THAN", Threshold: $pct, ThresholdType: "PERCENTAGE"},
    Subscribers: [{SubscriptionType: "EMAIL", Address: $email}]}'
}
create() { # create <name> <budget json> <notification json...>
  local name=$1 body=$2; shift 2
  if aws budgets describe-budget --account-id "$ACCOUNT" --budget-name "$name" > /dev/null 2>&1; then
    aws budgets update-budget --account-id "$ACCOUNT" --new-budget "$body"
    echo "updated $name"
  else
    aws budgets create-budget --account-id "$ACCOUNT" --budget "$body" \
      --notifications-with-subscribers "$(jq -s . <<< "$*")"
    echo "created $name"
  fi
}

create Account-Credit-Use "$(budget Account-Credit-Use "$CREDIT_USE_LIMIT" false)" \
  "$(alert ACTUAL 50)" "$(alert ACTUAL 80)" "$(alert ACTUAL 100)" "$(alert FORECASTED 100)"
create Account-Out-Of-Pocket "$(budget Account-Out-Of-Pocket 1 true)" "$(alert ACTUAL 1)"
aws budgets describe-budgets --account-id "$ACCOUNT" \
  --query 'Budgets[].[BudgetName, BudgetLimit.Amount, CostTypes.IncludeCredit]' --output table
