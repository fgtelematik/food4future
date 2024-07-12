#!/bin/bash
#
# f4f Study Companion Backend Server
#
# Copyright (c) 2024 Technical University of Applied Sciences Wildau
# Author: Philipp Wagner, Research Group Telematics
# Contact: fgtelematik@th-wildau.de
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation version 2.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#

cd "$(dirname "$0")/.."

# !! WARNING !! Only run this script using Ubuntu Bionic or higher.

# Set default ports
LOCAL_PORT=8000
PUBLIC_PORT=8443
SERVICE_SUFFIX=""
# REQUIRED_PYTHON_VERSION=$(grep 'PYTHON_VERSION' Pipfile | awk -F '"' '{print $2}') # Might be needed in future

# Read optional command line arguments:
# 	-p: custom public port
# 	-l: custom local port
# 	-s: service suffix (allows registering multiple instances, affects nginx site and systemd service)
while getopts "p:l:s:" option; do
  case $option in
    p )
    PUBLIC_PORT=$OPTARG
    echo "Set public port to: $PUBLIC_PORT"
    ;;
    l )
    LOCAL_PORT=$OPTARG
    echo "Set local port to: $LOCAL_PORT"
    USE_CUSTOM_LOCAL_PORT=1
    ;;
    s )
    SERVICE_SUFFIX="-$OPTARG"
    ;;
  esac
done


# Pre-checks of system environment
# --------------------------------

if [ $USER != 'root' ]
then
    echo "You must be root to install f4f service."
    exit
fi

command -v python3 >/dev/null 2>&1
if [ ! $? -eq 0 ]
then
    echo "Python3 not found. Please install python3 package first."
    exit
fi

if [ ! -d "/etc/nginx/sites-available/"  ]
then
    echo "nginx not found. Please install nginx package first."
    exit
fi

if [ ! -d "/lib/systemd/system/"  ]
then
    echo "Systemd service folder not found were it was expected to be. Are you using Ubuntu 18.04 or higher?"
    exit
fi

# Determine required Python version, for later using pyenv to install it
PYTHON_VERSION_line=$(grep "python_full_version" "Pipfile")
PYTHON_VERSION=$(echo "$PYTHON_VERSION_line" | awk -F' = ' '{print $2}' | tr -d '"')


# Installation
# ------------

echo Create user 'f4f' if not exists ...
id -u f4f &>/dev/null || useradd --system --user-group f4f

echo Installing pipenv for user 'f4f', if not already installed ...
sudo -H -u f4f bash -c 'pip install --user pipenv'

if [ ! -d "/home/f4f/.pyenv/"  ]
then
    echo Installing pyenv for user 'f4f' ...
    sudo -H -u f4f bash -c 'curl https://pyenv.run | bash'
else
    echo "pyenv found at: /home/f4f/.pyenv/"
fi

if sudo -H -u f4f bash -c "/home/f4f/.pyenv/bin/pyenv versions | grep $PYTHON_VERSION"; then
  echo "Python $PYTHON_VERSION is already in the list of installed pyenv python versions."
else
  echo "Installing Python $PYTHON_VERSION using pyenv, plese wait ..."
  sudo -H -u f4f bash -c "/home/f4f/.pyenv/bin/pyenv install $PYTHON_VERSION"

  if [ ! $? -eq 0 ]
  then
      echo "Python installation failed or canceled."
      exit
  fi
fi

echo Preparing virtualenv ...
sudo -H -u f4f bash -c 'export PIPENV_PYTHON="$(/home/f4f/.pyenv/bin/pyenv root)/shims/python" && pipenv sync'


if [ ! $? -eq 0 ]
then
    echo "Error preparing the virtual python environment. If you're using Pyenv please check the workaround at: https://blog.pancho.name/posts/workaround-for-issue-with-pipenv-pyenv/"
    exit
fi

if [ ! -e "config.json" ]
then
	echo Create default config.js
	cp -a schema/default_config.json config.json

  if [ -n "$USE_CUSTOM_LOCAL_PORT" ]
  then
  echo Set default port in config.json to: $USE_CUSTOM_LOCAL_PORT
  sed -i "s/8000/$USE_CUSTOM_LOCAL_PORT/g" config.json
  fi

else

  if [ -n "$USE_CUSTOM_LOCAL_PORT" ]
  then
  echo WARNING: config.json already exists, but custom local port was specified. Please manually update config.json to use the custom port.
  fi

fi

if [ ! -e ".git/hooks/post-merge" ]
then
	echo Register git post-update script-hook
    ln -s "$PWD/scripts/post-merge.sh" "$PWD/.git/hooks/post-merge"
fi


# make f4f owner
chown -R f4f:f4f .

echo Preparing systemd service ...

# copy systemd services and make them owned by root
cp scripts/f4fserver.service .
cp scripts/f4fsetfacl.service .

# replace absolute app path in copied service files
sed -i "s~{app_dir}~$PWD~"  f4fserver.service
sed -i "s~{app_dir}~$PWD~"  f4fsetfacl.service

# move services to systemd service folder (WARNING: optimized for Ubuntu, currently no OS detection)
mv "f4fserver.service" "/lib/systemd/system/f4fserver${SERVICE_SUFFIX}.service"
mv "f4fsetfacl.service" "/lib/systemd/system/f4fsetfacl${SERVICE_SUFFIX}.service"
systemctl daemon-reload

# auto-enable setfacl service to gain r/w access of app path for user "f4f-dev"
systemctl enable "f4fsetfacl${SERVICE_SUFFIX}.service" >/dev/null
systemctl start "f4fsetfacl${SERVICE_SUFFIX}.service" >/dev/null

echo Configuring nginx ...

# copy site config and make it owned by root
cp scripts/f4fserver-nginx.conf .

# replace public and local port in copied nginx config file
sed -i "s~{f4flocalport}~$LOCAL_PORT~"  f4fserver-nginx.conf
sed -i "s~{f4fpublicport}~$PUBLIC_PORT~"  f4fserver-nginx.conf
sed -i "s~{upstreamname}~f4fserver$SERVICE_SUFFIX~"  f4fserver-nginx.conf

# install & activate reverse proxy service in nginx
mv "f4fserver-nginx.conf" "/etc/nginx/sites-available/f4fserver${SERVICE_SUFFIX}.conf"
ln -sf "/etc/nginx/sites-available/f4fserver${SERVICE_SUFFIX}.conf" "/etc/nginx/sites-enabled/f4fserver${SERVICE_SUFFIX}.conf"

# restart nginx if already running
systemctl is-active --quiet nginx && systemctl restart nginx

echo Setup complete.\n
printf "Start f4f backend server and nginx using following command:\n\tsystemctl start f4fserver${SERVICE_SUFFIX}\n\n"
