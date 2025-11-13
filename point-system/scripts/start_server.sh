#!/bin/bash
echo "Starting payment-system.service via systemctl..."

# (중요) 요청하신 대로 환경 변수 섹션이 모두 제거되었습니다.
# 환경 변수는 /etc/systemd/system/payment-system.service 파일이 관리합니다.

# systemd 데몬 리로드 (서비스 파일 자체가 변경되었을 수도 있으므로)
sudo systemctl daemon-reload

# 서비스 시작
sudo systemctl start payment-system

echo "Service start command issued."

