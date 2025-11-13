#!/bin/bash
echo "Running BeforeInstall script..."

APP_DIR="/home/ec2-user/app"

# 대상 디렉터리가 존재하는지 확인
if [ -d "$APP_DIR" ]; then
  echo "Cleaning up old deployment files in $APP_DIR..."
  # 디렉터리 내의 모든 파일을 삭제합니다. (숨김 파일 포함)
  # rm -rf "$APP_DIR"/* # 위 명령어가 위험할 수 있으니, 디렉터리 자체를 지우고 새로 만듭니다.
  rm -rf "$APP_DIR"
fi

echo "Creating deployment directory $APP_DIR..."
mkdir -p "$APP_DIR"
chown ec2-user:ec2-user "$APP_DIR"

echo "BeforeInstall script finished."
