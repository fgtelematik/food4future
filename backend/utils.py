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
import re
import smtplib
import ssl
import traceback

import unicodedata
from time import time
from urllib import parse

from fastapi import Request
from fastapi.staticfiles import StaticFiles
from pyaxmlparser import APK
from starlette.exceptions import HTTPException

from config import Config, ConfigParam

TOKEN_KEY = "tk"
APK_VERSION_CODE_MAX_AGE_SECONDS = 300.0  # check every 5 minutes for new uploaded APK version

_apk_version_code_cached = None
_apk_version_code_cache_time = time()


def debug_print(*args):
    if Config.getValue(ConfigParam.DebugOutput):
        print(*args)


def get_base_url(request: Request):
    # If public URL setting is set, we use this as base URL
    public_url = Config.getValue(ConfigParam.PublicBaseUrl)
    if public_url and isinstance(public_url, str):
        if not public_url.endswith("/"):
            public_url += "/"
        return public_url

    # If public URL setting is not set, we try to determine from HTTP request
    parsed_url = parse.urlparse(str(request.url))
    base_url = '{uri.scheme}://{uri.netloc}/'.format(uri=parsed_url)
    return base_url


def get_apk_url(request: Request):
    return get_base_url(request) + "apk/" + Config.getValue(ConfigParam.APKFile)


def send_email(receiver: str, message: str):
    smtp_server = Config.getValue(ConfigParam.SMTP_Server)
    smtp_port = Config.getValue(ConfigParam.SMTP_Port)
    sender = Config.getValue(ConfigParam.SMTP_From)

    if not smtp_server or not smtp_port or not sender:
        logging.warning("Warning: Not sending Registration E-Mail since no SMTP server was configured.")
        return False

    smtp_user = Config.getValue(ConfigParam.SMTP_User)
    smtp_password = Config.getValue(ConfigParam.SMTP_Password)
    smtp_use_starttls = Config.getValue(ConfigParam.SMTP_Use_Starttls)

    if not smtp_password:
        smtp_password = ""

    # Create a secure SSL context
    context = ssl.create_default_context()
    server = None
    try:
        if smtp_use_starttls:
            server = smtplib.SMTP(smtp_server,smtp_port)
            server.ehlo()
            server.starttls(context=context)
            server.ehlo()
        else:
            server = smtplib.SMTP_SSL(smtp_server, smtp_port, context=context)

        if smtp_user:
            server.login(smtp_user, smtp_password)

        server.sendmail(sender, receiver, message)

        return True
    except Exception as e:
        logging.error("Error on sending mail: " + str(e))
        logging.error(traceback.format_exc())
        return False
    finally:
        try:
            if server:
                server.quit()
        except Exception as e:
            logging.error("Error on closing SMTP connection: " + str(e))


def get_apk_url_with_token(request: Request, token: str):
    apk_url = get_apk_url(request)
    params = {TOKEN_KEY: token}
    url_parse = parse.urlparse(apk_url)
    query = url_parse.query
    url_dict = dict(parse.parse_qsl(query))
    url_dict.update(params)
    url_new_query = parse.urlencode(url_dict)
    url_parse = url_parse._replace(query=url_new_query)
    return parse.urlunparse(url_parse)


def get_apk_version_code():
    global _apk_version_code_cached, _apk_version_code_cache_time
    now = time()
    if _apk_version_code_cached and _apk_version_code_cache_time + APK_VERSION_CODE_MAX_AGE_SECONDS > now:
        # Parsing the APK is expensive, so we cache the version code extracted from APK
        return _apk_version_code_cached

    try:
        apk = APK("apk/" + Config.getValue(ConfigParam.APKFile))
    except:
        # Return none if APK could not be found or is inaccessible or unparsable
        return None

    _apk_version_code_cached = apk.version_code
    _apk_version_code_cache_time = now

    return _apk_version_code_cached


def is_valid_email(email: str):
    return re.match(r"[^@]+@[^@]+\.[^@]+", email) is not None


def dict_without_keys(d: dict, keys: list[str]):
    return {x: d[x] for x in d if x not in keys}


def dict_only_keys(d: dict, keys: list[str]):
    return {x: d[x] for x in d if x in keys}


# This is a workaround for SPA routing
# See https://stackoverflow.com/a/68363904/5106474
class SPAStaticFiles(StaticFiles):
    async def get_response(self, path: str, scope):
        try:
            response = await super().get_response(path, scope)
        except HTTPException as error:
            if error.status_code == 404:
                response = await super().get_response('.', scope)
            else:
                raise error

        return response


def sanitize_filename(value: str):
    value = unicodedata.normalize('NFKD', value).encode('ascii', 'ignore').decode('ascii')
    value = re.sub('[^\w\s-]', '', value).strip().lower()
    value = re.sub('[-\s]+', '-', value)
    return value
