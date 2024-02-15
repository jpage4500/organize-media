cd "$(dirname $0)"

PI=pi@192.168.0.103
SCRIPTS=/home/pi/tmpScripts

echo "copy to ${PI}?"
echo "** press any key **"
echo
read n

echo "copying to PI.."
scp ../out/artifacts/organize_media_jar/organize-media.jar ${PI}:/home/pi

echo "moving to /usr/local/bin"
ssh ${PI} "sudo mv /home/pi/organize-media.jar /usr/local/bin/organize-media.jar"

echo "chown"
ssh ${PI} "sudo chown pi /usr/local/bin/organize-media.jar"
