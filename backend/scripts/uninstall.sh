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

SERVICE_SUFFIX=""

# Read optional command line arguments:
# 	-s: service suffix (allows identify the right instance if multiple instances are registered)
while getopts "s:" option; do
  case $option in
    s ) 
    SERVICE_SUFFIX="-$OPTARG"
    ;;
  esac
done

if [ $USER != 'root' ]
then
    echo "You must be root to uninstall f4f service."
    exit
fi

echo "Trying to uninstall f4fserver${SERVICE_SUFFIX} systemd daemon and nginx config..."

SERVICE_FILE_NAME="f4fserver${SERVICE_SUFFIX}.service"
NGINX_FILE_NAME="f4fserver${SERVICE_SUFFIX}.conf"

# Remove systemd service
(systemctl is-active --quiet $SERVICE_FILE_NAME && echo "Stopping $SERVICE_FILE_NAME" && systemctl stop $SERVICE_FILE_NAME) || true
(rm "/lib/systemd/system/${SERVICE_FILE_NAME}" && systemctl daemon-reload && echo "Removed systemd service successfully") || true

# Remove nginx site
rm "/etc/nginx/sites-enabled/${NGINX_FILE_NAME}" || true
(rm "/etc/nginx/sites-available/${NGINX_FILE_NAME}" && echo "Removed nginx service") || true

# restart nginx if already running
systemctl is-active --quiet nginx && systemctl restart nginx

echo "Removing virtualenv..."
sudo -H -u f4f bash -c 'pipenv --rm'

echo "Uninstallation process done."