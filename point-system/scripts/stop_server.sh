#!/bin/bash
echo "Stopping payment-system.service via systemctl..."

# systemctl is-active --quiet는 서비스가 실행 중이면 0을 반환합니다.
if systemctl is-active --quiet payment-system; then
  # sudo: ec2-user가 systemctl을 실행할 수 있도록 sudoers에 등록되어 있어야 합니다.
  # (예: /etc/sudoers.d/codedeploy-agent)
  sudo systemctl stop payment-system
  echo "Service stopped."
else
  echo "Service is not running. Nothing to stop."
fi

