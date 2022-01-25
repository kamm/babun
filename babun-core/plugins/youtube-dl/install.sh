set -e -f -o pipefail
source "/usr/local/etc/babun.instance"
source "$babun_tools/script.sh"

curl https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe  > /usr/local/bin/yt-dlp.exe 2>/dev/null
curl https://yt-dl.org/downloads/2021.12.17/youtube-dl.exe > /usr/local/bin/youtube-dl.exe 2>/dev/null

chmod 755 /usr/local/bin/yt-dlp.exe
chmod 755 /usr/local/bin/youtube-dl.exe

curl https://github.com/GyanD/codexffmpeg/releases/download/5.0/ffmpeg-5.0-essentials_build.zip > ffmpeg-5.0-essentials_build.zip

unzip -j ffmpeg-5.0-essentials_build.zip ffmpeg-5.0-essentials_build/bin/ffmpeg.exe
unzip -j ffmpeg-5.0-essentials_build.zip ffmpeg-5.0-essentials_build/bin/ffprobe.exe
unzip -j ffmpeg-5.0-essentials_build.zip ffmpeg-5.0-essentials_build/bin/ffplay.exe

rm -f ffmpeg-5.0-essentials_build.zip

mv ffprobe.exe ffplay.exe ffmpeg.exe /usr/local/bin

chmod 775 /usr/local/bin/ffmpeg.exe
chmod 775 /usr/local/bin/ffprobe.exe
chmod 775 /usr/local/bin/ffpley.exe



