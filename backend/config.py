#  f4f Study Companion Backend Server
#
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

import json
from os import path, environ
from shutil import copyfile

DEFAULT_CONFIG_FILE = "schema/default_config.json"
CONFIG_FILE = "config.json"
ENV_PREFIX = "F4F_"

class ConfigParam:
    DbHost = "mongodb_host"
    DbPort = "mongodb_port"
    DbUser = "mongodb_username"
    DbPassword = "mongodb_password" 
    DbName = "mongodb_database" 
    DbAuthSource = "mongodb_authsource"
    DbSSHTunnelActive = "mongodb_sshtunnel_active"
    DbSSHTunnelHost = "mongodb_sshtunnel_host"
    DbSSHTunnelUser = "mongodb_sshtunnel_user"
    DbSSHTunnelPassword = "mongodb_sshtunnel_password"
    AutoReload = "server_autoreload"
    Host = "server_host"
    Port = "server_port"
    SSLKeyFile = "ssl_private_key_file"
    SSLPassword = "ssl_private_key_password"
    SSLCertFile = "ssl_cert"
    Logfile = "logfile"
    APKFile = "apk_file"
    WebAppPath = "web_app_path"
    PublicBaseUrl = "public_base_url"
    DebugOutput = "debug_output" # Currently only used on very specific parts during development.
    SMTP_Server = "smtp_server"
    SMTP_Port = "smtp_port"
    SMTP_From = "smtp_from"
    SMTP_Use_Starttls = "smtp_use_starttls"
    SMTP_User = "smtp_user"
    SMTP_Password = "smtp_password"
    ResetManagerUsername = "reset_manager_username"
    ResetManagerPassword = "reset_manager_password"

class Config:
    configdata = dict()

    @staticmethod
    def _readFromFile():
        if not path.isfile(CONFIG_FILE):
            copyfile(DEFAULT_CONFIG_FILE, CONFIG_FILE)
        with open(CONFIG_FILE, 'r') as f:
            Config.configdata = json.load(f)

    @staticmethod
    def _updateDefaultValue(param : str):
        ''' Add new default value to config for a field, which hasn't existed before (e.g. after app update).  '''

        # re-read current config from file system
        Config._readFromFile()

        # read missing value from default config file
        with open(DEFAULT_CONFIG_FILE, 'r') as f:
            defaultdata = json.load(f)
        
        # store value in cached config
        Config.configdata[param] = defaultdata[param]

        # write cached config to config file (in human-optimized format)
        with open(CONFIG_FILE, 'w') as f:
            json.dump(Config.configdata, f, indent=4, sort_keys=True)

    @staticmethod
    def getValue(param : str):
        # First Check, if parameter is set as environment variable
        if ENV_PREFIX + param.upper() in environ:
            return environ[ENV_PREFIX + param.upper()]

        # Check, if parameter is set in config file
        if len(Config.configdata) == 0:
            Config._readFromFile()

        # Import value from default config into config file,
        # if not already there
        if param not in Config.configdata:
            Config._updateDefaultValue(param)
        
        value = Config.configdata[param]

        return value

