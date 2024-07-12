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

import array
import datetime
import hashlib
from functools import lru_cache
from secrets import token_urlsafe
from typing import Final

from bson.objectid import ObjectId
from fastapi import Depends, HTTPException, Response, APIRouter
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm

from enums import Role, LogInMode
from models import convertJSONToMongo, convertMongoToJSON
from models import db, User, Session

router = APIRouter(
    tags=["Session Management"],
)

# NOTE:
# This router implementation was added at an early project stage and has a lot of bad structure and formatting
# and the interface neither properly typed nor documented.
# It is working as is, but requires a full re-implementation (as already done for other routers) also for better
# compliance with OAuth2.0.

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/token?openapi=1", auto_error=False)
TOKEN_SALT: Final = "42h,56$gztrh#sdfh-+235gs"  # static salt used for hashing of session token


@router.post("/token")
def generateToken(response: Response, form_data: OAuth2PasswordRequestForm = Depends(), openapi: bool = False):
    user = getUserByName(form_data.username)

    if user == None or (hashPassword(form_data.password, user.salt) != user.password_hash):
        raise HTTPException(status_code=401, detail="Incorrect username or password")

    newtoken = token_urlsafe(16)

    db.session.create_index('hashed_token', unique=True)

    session_data = {
        "user": user.username,
        "user_id": user.id,
        "hashed_token": hashPassword(newtoken, TOKEN_SALT),
        "creation_time": datetime.datetime.utcnow(),
        "num_requests": 1,
        "login_mode": LogInMode.Credentials.name
    }

    if openapi:
        # Wokraround: Allow only one session per user for OpenAPI to avoid keeping no longer used Swagger sessions
        db.session.delete_many({"user_id": user.id, "openapi": True})
        session_data['openapi'] = True

    db.session.insert_one(session_data)

    response.headers["Cache-Control"] = "no-store"
    response.headers["Pragma"] = "no-cache"

    return {"access_token": newtoken, "token_type": "bearer"}  # , "expires_in" : "3600"}


def destroyToken(response: Response, token: str = Depends(oauth2_scheme)) -> bool:
    if not token:
        return False
    hashedToken = hashPassword(token, TOKEN_SALT)
    session = db.session.find_one({"hashed_token": hashedToken})
    if session == None:  # No session exists for this token
        return False
    response.headers["Cache-Control"] = "no-store"
    response.headers["Pragma"] = "no-cache"
    if "login_mode" in session and session["login_mode"] == LogInMode.EMailToken.name:
        return True  # Permanent sessions can be re-used and will not be destroyed on logout requests
    res = db.session.delete_one({"hashed_token": hashedToken})
    return res.deleted_count > 0


@lru_cache(maxsize=128)
def hashPassword(password: str, salt: str):
    res2 = hashlib.pbkdf2_hmac(
        'sha256',  # The hash digest algorithm for HMAC
        array.array('B', password.encode()),  # Convert the password to bytes
        array.array('B', salt.encode()),  # Provide the salt
        100000  # It is recommended to use at least 100,000 iterations of SHA-256
    )
    return res2.hex()


def getUserByName(name: str) -> User:
    if db.user.count_documents({"username": name}) == 0:
        return None
    else:
        users = db.user.find({"username": name})
        return User(**users[0])


class _CurrentUserDependency:
    def __init__(self, authenticationRequired: bool = True):
        self.authenticationRequired = authenticationRequired
        pass

    def __call__(self, token: str = Depends(oauth2_scheme)) -> User:
        session = None

        if token:
            hashed_token = hashPassword(token, TOKEN_SALT)
            session = db.session.find_one({"hashed_token": hashed_token})

        if session == None:
            if self.authenticationRequired:
                raise HTTPException(status_code=401, detail="Not authenticated")
            else:
                return None

        num_requests = 0
        if 'num_requests' in session:
            num_requests = int(session['num_requests'])

        num_requests = num_requests + 1

        session = Session(**session)

        db.session.update_one({"hashed_token": hashed_token},
                              {"$set": {"last_use": datetime.datetime.utcnow(), "num_requests": num_requests}})

        currentuser_obj = getUserByName(session.user)

        if currentuser_obj == None:
            # User was deleted. Destroy session.
            db.session.delete_one({"hashed_token": hashPassword(token, TOKEN_SALT)})
            if self.authenticationRequired:
                raise HTTPException(status_code=401, detail="User does not exist.")
            else:
                return None

        return currentuser_obj


# export dependency instances for non-optional and optional current user
currentUser = _CurrentUserDependency(True)  # non-optional currentUser will raise exception immediately if unauthorized
currentUserOptional = _CurrentUserDependency(False)  # currentUserOptional will be None if unauthorized


def generateTokenForUser(target_user: dict, current_user: User, permanent: bool = False):
    if current_user == None or current_user.role != Role.Administrator and current_user.role != Role.Nurse and \
            target_user['_id'] != current_user['_id']:
        raise HTTPException(status_code=403, detail="Operation not permitted.")

    newtoken = token_urlsafe(16)

    loginMode = LogInMode.EMailToken if permanent else LogInMode.AppGeneratedToken

    if permanent:
        # make sure, only one session with login_mode=EMailToken exists
        db.session.delete_many({'user_id': target_user['_id'], 'login_mode': LogInMode.EMailToken.name})
    else:
        # delete former app-generated QR codes, which never had been used 
        db.session.delete_many(
            {'user_id': target_user['_id'], 'login_mode': LogInMode.AppGeneratedToken.name, 'num_requests': 0})

    db.session.insert_one({
        "user": target_user['username'],
        "user_id": target_user['_id'],
        "hashed_token": hashPassword(newtoken, TOKEN_SALT),
        "creation_time": datetime.datetime.utcnow(),
        "num_requests": 0,
        "login_mode": loginMode.name})

    return {"access_token": newtoken}


def login(res=Depends(generateToken)):
    return res


@router.get("/token/{user_id}")
def token(user_id: str, current_user: User = Depends(currentUser)):
    if not ObjectId.is_valid(user_id):
        raise HTTPException(status_code=404, detail="Invalid user id.")

    target_user = {"id": user_id}
    convertJSONToMongo(target_user)
    target_user = db.user.find_one(target_user)

    if target_user == None:
        raise HTTPException(status_code=404, detail="Invalid user id.")

    return generateTokenForUser(target_user, current_user)
