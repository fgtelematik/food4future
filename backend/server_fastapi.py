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

import io
import logging
import os.path

import qrcode
from Cheetah.Template import Template
from fastapi.applications import FastAPI
from fastapi.exceptions import HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.param_functions import Depends
from fastapi.requests import Request
from starlette.responses import Response, PlainTextResponse, HTMLResponse
from starlette.staticfiles import StaticFiles

import utils
from config import Config, ConfigParam
from license_info_generator import get_license_info
from models import User
from routers import session
from routers.data_router import router as data_router_v2
from routers.dataprovider import router as data_router_v1
from routers.datasync import router as datasync_router
from routers.forms_router import router as forms_router
from routers.legacy_router import legacy_schema_router
from routers.schema_router import router as schema_router
from routers.session import router as session_router
from routers.user_router import router as user_router, update_user_client_version

# Parameters returned from GET /info:
# The API_VERSION should be incremented whenever the API changed in a way that breaks backwards compatibility
API_VERSION = 3  # Field: api_version

# The MIN_APP_VERSION should be incremented whenever theAPI changed in a way it breaks
# backwards compatibility with older versions of the Android App
MIN_ANDROID_APP_VERSION = 35  # Field: min_android_app_version

# The API_NAME lets the client simply check if it is talking to a valid f4f backend API and should not be changed
API_NAME = "f4f-server"  # Field: api_name

app = FastAPI()

# -- Modularized Routers -- #
app.include_router(schema_router)
app.include_router(legacy_schema_router)
app.include_router(user_router)
app.include_router(data_router_v1)  # DEPRECATED!! (EP: /data/)
app.include_router(data_router_v2)  # (EP: /data/v2/)
app.include_router(session_router)
app.include_router(datasync_router)
app.include_router(forms_router)

OTHER_INTERNAL_ENDPOINT_TAG = "Other Internally Used Endpoints"

# -- Mount static files directory -- #
app.mount("/images", StaticFiles(directory="images"), name="images")

# -- Mount app download apk directory -- #
app.mount("/apk", StaticFiles(directory="apk"), name="apk")

# -- Mount static webapp if available -- #
web_app_path = Config.getValue(ConfigParam.WebAppPath)
if os.path.exists(web_app_path):
    app.mount("/manage/", utils.SPAStaticFiles(directory=web_app_path, html=True), name="webapp")

# -- CORS (for development) -- #
origins = [
    "http://localhost:5173",
    "http://localhost:5183",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["Content-Disposition"],  # allow server-defined file names for file downloads
)


@app.get("/download_app_qr.png", include_in_schema=False)
async def download_app_qr(apk_url=Depends(utils.get_apk_url)):
    qr_img = qrcode.make(apk_url)
    qr_img_bytes = io.BytesIO()
    qr_img.save(qr_img_bytes)
    qr_img_data = qr_img_bytes.getvalue()
    return Response(content=qr_img_data, media_type="image/png")


@app.post("/log", tags=[OTHER_INTERNAL_ENDPOINT_TAG])
async def log_server(request: Request, current_user: User = Depends(session.currentUserOptional)):
    anon_key = 'SQD3ib67ttxvkSpln2K7cw'  # must be same in the app

    req = await request.json()
    client_version_str = ""
    if 'client_version' in req:
        client_version = req['client_version']
        if type(client_version) == int:
            client_version_str = ", v" + str(client_version)
            update_user_client_version(current_user, client_version)

    if 'msg' in req:
        logmsg = req['msg']

        if current_user is None:
            if 'anon_key' not in req or req['anon_key'] != anon_key:
                raise HTTPException(status_code=401, detail="Missing or invalid authentication for anonymous.")
            logmsg = "(Unauthenticated User" + client_version_str + "): " + logmsg
        else:
            logmsg = "UserID: " + str(current_user.id) + client_version_str + ", " + logmsg
        logging.info(logmsg)
        print(logmsg)
        return {"success": True}
    else:
        raise HTTPException(status_code=400, detail="No log message specified.")


@app.get("/info", tags=[OTHER_INTERNAL_ENDPOINT_TAG])
async def info(req: Request):
    return {
        "api_version": API_VERSION,
        "api_name": API_NAME,
        "min_android_app_version": MIN_ANDROID_APP_VERSION,
        "public URL": utils.get_base_url(req)
    }


# License Info text
@app.get("/license_info", response_class=PlainTextResponse, include_in_schema=False)
async def license():
    license_text = get_license_info()
    return PlainTextResponse(content=license_text)


# Main Page
@app.get("/", response_class=PlainTextResponse, include_in_schema=False)
async def root(apk_url=Depends(utils.get_apk_url)):
    with open("schema/index.html.template", 'r', encoding='utf8') as templateFile:
        template_def = templateFile.read()

    tpl = Template(template_def)
    tpl.apk_url = apk_url

    return HTMLResponse(content=str(tpl))
