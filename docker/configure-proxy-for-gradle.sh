#!/bin/bash

#  Copyright (c) 2024 Technical University of Applied Sciences Wildau
#  Author: Philipp Wagner, Research Group Telematics
#  Contact: fgtelematik@th-wildau.de
#
#  This program is free software; you can redistribute it and/or
#  modify it under the terms of the GNU General Public License
#  as published by the Free Software Foundation version 2.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program; if not, write to the Free Software
#  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.


extract_url_parts() {
    # Check if a URL is provided
    if [ -z "$1" ]; then
        echo "Error: No URL provided."
        return 1
    fi

    # Remove protocol part (http://, https://, ftp://, etc.)
    url_without_protocol="${1#*://}"

    # Initialize user and password to empty values
    user=""
    password=""

    # Extract user and password (if specified)
    if [[ "$url_without_protocol" == *@* ]]; then
        user_password_part="${url_without_protocol%%@*}"

        if [[ "$user_password_part" == *:* ]]; then
            user="${user_password_part%%:*}"
            password="${user_password_part#*:}"
        else
            user="$user_password_part"
        fi

        # Remove user/password part from the URL
        url_without_protocol="${url_without_protocol#*@}"
    fi

    # Extract host and port
    host_port_path_part="${url_without_protocol%%/*}"
    host_port_part="${host_port_path_part##*:}"

    if [ "$host_port_part" != "$host_port_path_part" ]; then
        port="$host_port_part"
    else
        # If port is not specified, derive from protocol
        protocol="${1%%://*}"
        case "$protocol" in
            http) port=80 ;;
            https) port=443 ;;
            ftp) port=21 ;;
            *) echo "Unknown protocol: $protocol"; return 1 ;;
        esac
    fi

    # Set host to the remaining part after removing the port
    host="${host_port_path_part%:*}"

    # Print the extracted parts
    echo "$host $port $user $password"
}

# Create gradle config directory
mkdir -p ~/.gradle

# Check if proxy environment variables are set
if [ -n "$HTTP_PROXY" ] || [ -n "$HTTPS_PROXY" ]; then
  # Proxy is set, create gradle.properties with proxy configuration
  read -r host port user password <<< $(extract_url_parts $HTTP_PROXY)

  echo "systemProp.http.proxyHost=$host" > ~/.gradle/gradle.properties
  echo "systemProp.http.proxyPort=$port" >> ~/.gradle/gradle.properties

  if [ -n "$user" ]; then
    echo "systemProp.http.proxyUser=$user" >> ~/.gradle/gradle.properties
    echo "systemProp.http.proxyPassword=$password" >> ~/.gradle/gradle.properties
  fi

  read -r host port user password <<< $(extract_url_parts $HTTPS_PROXY)

  echo "systemProp.https.proxyHost=$host" >> ~/.gradle/gradle.properties
  echo "systemProp.https.proxyPort=$port" >> ~/.gradle/gradle.properties

  if [ -n "$user" ]; then
    echo "systemProp.https.proxyUser=$user" >> ~/.gradle/gradle.properties
    echo "systemProp.https.proxyPassword=$password" >> ~/.gradle/gradle.properties
  fi

  echo "systemProp.http.nonProxyHosts=$NO_PROXY" >> ~/.gradle/gradle.properties
  echo "systemProp.https.nonProxyHosts=$NO_PROXY" >> ~/.gradle/gradle.properties
else
  # No proxy, create an empty gradle.properties file in the home directory
  touch ~/.gradle/gradle.properties
fi

