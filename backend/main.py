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

import logging
import uvicorn

import os
import shutil

# this import triggers a database connection attempt and raises an exception if failed
# and therefore prevents starting uvicorn if database is not accessible
import models

from config import Config, ConfigParam
from license_info_generator import generate_license_info
from routers.user_router import create_or_update_admin_user

logging.basicConfig(filename=Config.getValue(ConfigParam.Logfile), level=logging.INFO, format='%(asctime)s %(message)s')

generate_license_info()

# Copy preset image files
preset_images_target = "images/preset"
if not os.path.exists(preset_images_target):
    shutil.copytree("images_preset", preset_images_target)

def writePidFile():
    try:
        pid = str(os.getpid())
        f = open('f4fserver.pid', 'w')
        f.write(pid)
        f.close()
    except:
        logging.warn("Could not update PID file.")


if __name__ == "__main__":
    writePidFile()
    host = Config.getValue(ConfigParam.Host)
    port = Config.getValue(ConfigParam.Port)
    autoreload = Config.getValue(ConfigParam.AutoReload)
    sslkey = Config.getValue(ConfigParam.SSLKeyFile)
    sslpassword = Config.getValue(ConfigParam.SSLPassword)
    sslcert = Config.getValue(ConfigParam.SSLCertFile)

    admin_username = Config.getValue(ConfigParam.ResetManagerUsername)
    admin_password = Config.getValue(ConfigParam.ResetManagerPassword)

    if admin_username and admin_password:
        create_or_update_admin_user(admin_username, admin_password)


    # selected ciphers basically oriented at recommendation from: https://www.acunetix.com/blog/articles/tls-ssl-cipher-hardening/
    # but with removed ECDHE-RSA-AES128-SHA256 and ECDHE-RSA-AES256-SHA384,
    # due to weaknesses for these ciphers reported by ssllabs.com SSL report.
    # The removed ciphers are not relevant for Android clients.
    # ssl_ciphers = 'ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-SHA384:ECDHE-ECDSA-AES128-SHA256'
    # with this suite we had problems with the TLS handshake using MATLAB 

    # now we use cipher suite recommended by https://www.ssl.com/guide/tls-standards-compliance/#cipher-suites
    ssl_ciphers = 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-GCM-SHA384'

    uvicorn.run("server_fastapi:app", host=host, port=port, reload=autoreload, ssl_keyfile=sslkey, ssl_certfile=sslcert,
                ssl_keyfile_password=sslpassword, ssl_ciphers=ssl_ciphers, proxy_headers=True, forwarded_allow_ips='*')

    if models.tunnelServer is not None:
        # might have been initialized and started during "import models"
        models.tunnelServer.stop()
