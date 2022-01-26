set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe  > /usr/local/bin/yt-dlp.exe 2>/dev/null
curl -L https://yt-dl.org/downloads/2021.12.17/youtube-dl.exe > /usr/local/bin/youtube-dl.exe 2>/dev/null

chmod 755 /usr/local/bin/yt-dlp.exe
chmod 755 /usr/local/bin/youtube-dl.exe
ffmpeg="ffmpeg-5.0-essentials_build.zip"
curl -L https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-5.0-essentials_build.zip > /tmp/${ffmpeg} 2>/dev/null

unzip -j /tmp/${ffmpeg} ffmpeg-5.0-essentials_build/bin/ffmpeg.exe >/dev/null 2>&1
unzip -j /tmp/${ffmpeg} ffmpeg-5.0-essentials_build/bin/ffprobe.exe >/dev/null 2>&1
unzip -j /tmp/${ffmpeg} ffmpeg-5.0-essentials_build/bin/ffplay.exe >/dev/null 2>&1

rm -f /tmp/ffmpeg-5.0-essentials_build.zip

mv ffprobe.exe ffplay.exe ffmpeg.exe /usr/local/bin

chmod 775 /usr/local/bin/ffmpeg.exe
chmod 775 /usr/local/bin/ffprobe.exe
chmod 775 /usr/local/bin/ffplay.exe



