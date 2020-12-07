#!/bin/bash

# Check if this is ubuntu
hash lsb_release 2>/dev/null || { echo >&2 "This script only supports Ubuntu with lsb_release"; exit 3; }

os=`lsb_release -a 2>/dev/null | grep Distributor | cut -d ":" -f 2 | sed -e 's/\t//g'`
release=`lsb_release -a 2>/dev/null | grep Release | cut -d ":" -f 2 | sed -e 's/\t//g'`

if [[ ! $os == "Ubuntu" ]]
then
    echo "This script only supports Ubuntu"
    exit 2
fi

if [[ ! $release == "18.04" ]]
then
    echo "This script only supports Ubuntu 18.04 LTS"
    exit 2
fi

function drawHeader() {
    echo -e "\n\n"
    echo "+========================="
    echo "| "${1}
    echo "+========================="
}

drawHeader "Updating apt cache"
sudo apt update
drawHeader "Installing unzip"
sudo apt install -y unzip
drawHeader "Installing git"
sudo apt install -y git

drawHeader "Installing Java"
sudo apt install -y default-jre
javaloc=`update-alternatives --config java | head -n 1 | cut -d":" -f 2 | sed -e 's/^[ \t]*//' | sed -e 's/\/bin\/java//g'`
export JAVA_HOME=${javaloc} 
echo JAVA_HOME=${javaloc} >>~/.bashrc
echo PATH=$PATH:$JAVA_HOME/bin >>~/.bashrc
export PATH=$PATH:$JAVA_HOME/bin
. ~/.bashrc

drawHeader "Installing Maven"
sudo apt install -y maven
mvn -v

drawHeader "COMPLETE"
echo -e "\n\n\n\nPlease run the command\n\nsource ~/.bashrc\n\n"