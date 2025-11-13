#!/bin/bash
echo "Running AfterInstall script: Moving new JAR file..."

# 1. 변수 설정
DEPLOY_DIR="/home/ec2-user/app" # appspec.yml의 destination 경로

# 2. systemd 서비스 파일(.service)에 하드코딩된 'ExecStart' 경로
#    이 경로는 /etc/systemd/system/payment-system.service 파일의 경로와 정확히 일치해야 합니다.
TARGET_JAR_PATH="/home/ec2-user/sparta-cloud-backend/payment-system/build/libs/payment-system-0.0.1-SNAPSHOT.jar"
TARGET_DIR=$(dirname "$TARGET_JAR_PATH")

# 3. CodeDeploy가 배포한 새 JAR 파일 찾기
echo "Finding new JAR file in $DEPLOY_DIR..."
NEW_JAR=$(find "$DEPLOY_DIR" -name "*.jar" | head -n 1)

if [ -z "$NEW_JAR" ]; then
  echo "Error: No new .jar file found in $DEPLOY_DIR" >&2
  exit 1
fi

echo "Found new JAR: $NEW_JAR"

# 4. systemd가 참조하는 디렉터리 생성 (없을 경우)
if [ ! -d "$TARGET_DIR" ]; then
  echo "Creating target directory: $TARGET_DIR"
  mkdir -p "$TARGET_DIR"
  chown -R ec2-user:ec2-user "$(dirname "$TARGET_DIR")"
fi

# 5. 새 JAR 파일을 systemd 서비스 경로로 복사/교체
echo "Copying $NEW_JAR to $TARGET_JAR_PATH..."
cp "$NEW_JAR" "$TARGET_JAR_PATH"
chown ec2-user:ec2-user "$TARGET_JAR_PATH"

echo "New JAR file successfully deployed to $TARGET_JAR_PATH."

