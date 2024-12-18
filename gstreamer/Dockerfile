FROM ubuntu:latest

RUN apt-get update && apt-get install -y \
    gstreamer1.0-tools \
    gstreamer1.0-libav \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good \
    gstreamer1.0-plugins-bad \
    gstreamer1.0-plugins-ugly

WORKDIR /app

# 컨테이너가 계속 실행되도록 tail 명령 추가
CMD ["tail", "-f", "/dev/null"]