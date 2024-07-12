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
import random
import re
import secrets
import string
import traceback
from datetime import datetime
from email.mime.image import MIMEImage
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import List

import qrcode
from fastapi import APIRouter, Depends, HTTPException, Request

import utils
from config import Config, ConfigParam
from enums import Role, TestUsernameResult
from models import UserCreationResult, User, UserInput, db, convertJSONToMongo, UserBase, LegacyResult, \
    UserOutput, LegacyUserOutput, LegacyUsersOutput, ResendMailRequest, PyObjectId, to_mongo
from routers import session
from routers.session import hashPassword
from schema_v2.schema_models import Study

router = APIRouter(
    tags=["User Management"],
)


def create_or_update_admin_user(username: str, password: str):
    if not username or not password:
        raise Exception("Cannot create or update Admin user. Invalid username or password.")

    # Create a new user with the role admin (or update the existing one, if there is exactly one)
    num_admins = db.user.count_documents({'role': Role.Administrator.name})
    create_new = num_admins != 1
    update_admin_id = None

    if num_admins > 1:
        admins = db.user.find({'role': Role.Administrator.name})
        for admin in admins:
            db.session.delete_many({'user_id': admin['_id']})
        db.user.delete_many({'role': Role.Administrator.name})

    if not create_new:
        current_admin = db.user.find_one({'role': Role.Administrator.name})
        if (
                current_admin['username'] != username or
                hashPassword(password, current_admin['salt']) != current_admin['password_hash']
        ):
            # update admin user if username or password changed
            update_admin_id = current_admin['_id']

    if not create_new and not update_admin_id:
        # Admin user already exists and username and password are unchanged
        return

    salt = secrets.token_urlsafe(16)
    password_hash = session.hashPassword(password, salt)

    admin_user = User(
        username=username,
        role=Role.Administrator,
        send_registration_mail=False,
        password_hash=password_hash,
        salt=salt,
    )

    admin_user_data = to_mongo(admin_user)

    if create_new:
        logging.info("Creating new admin user '" + username + "'")
        db.user.insert_one(admin_user_data)

    if update_admin_id:
        logging.info("Updating admin user '" + username + "'")
        db.session.delete_many({'user_id': update_admin_id})
        db.user.update_one({'role': Role.Administrator.name}, {"$set": admin_user_data})


async def send_participant_registration_email(request: Request, user: UserBase, email: str, password: str | None,
                                              qr_url: str):
    # extract content data

    if not email:
        # No email address specified.
        return False

    username = user.username

    # extract participant's name if available (no longer supported due to privacy concerns)
    name = None

    # Prepare registration mail
    try:
        qr_data = qr_url
        qr_img = qrcode.make(qr_data)
        qr_img_bytes = io.BytesIO()
        qr_img.save(qr_img_bytes)
        qr_img_data = qr_img_bytes.getvalue()

        template_def = "(missing template)"
        subject = "(missing subject)"

        study = db.studies.find_one({"_id": user.study_id})
        if not study:
            raise HTTPException(status_code=500, detail="This user is not assigned to an existing study.")

        study = Study(**study)

        if user.role == Role.Participant.name:
            template_def = study.email_config.participant_body_template
            subject = study.email_config.participant_subject
        elif user.role == Role.Nurse.name:
            template_def = study.email_config.nurse_body_template
            subject = study.email_config.nurse_subject
        elif user.role == Role.Supervisor.name or user.role == Role.Administrator.name:
            template_def = study.email_config.scientist_body_template
            subject = study.email_config.scientist_subject

        password_str = password
        if not password_str:
            password_str = ""

        template_def = template_def.replace("$", "_")  # escape $ to avoid Cheetah error
        template_def = template_def.replace("%apk_url%", utils.get_base_url(request) + "app")
        template_def = template_def.replace("%backend_url%", utils.get_base_url(request) + "manage")
        template_def = template_def.replace("%username%", username)
        template_def = template_def.replace("%password%", password_str)

        no_password_pattern = r'%nopassword:(.*?)%'
        body = re.sub(no_password_pattern, r'\1' if not password_str else '', template_def)

        msg = MIMEMultipart()
        msg['Subject'] = subject
        msg['From'] = Config.getValue(ConfigParam.SMTP_From)
        msg['To'] = email

        text_part = MIMEText(body)
        qr_image_part = MIMEImage(qr_img_data)

        msg.attach(text_part)
        if user.role != Role.Supervisor.name:  # do not attach QR code for scientists
            msg.attach(qr_image_part)

    except Exception as e:
        logging.error("Error on preparing registration mail: " + str(e))
        logging.error(traceback.format_exc())
        return False

    return utils.send_email(msg['To'], msg.as_string())


def generate_or_get_username(user_input: UserInput = None):
    if user_input is not None and user_input.username:
        return user_input.username  # use pre-set username, if available

    do_generate = True
    username = ''

    while do_generate:
        username = ''.join(random.choices(string.ascii_lowercase, k=8))  # generate random 8 lower case ascii characters
        username = username + str(random.randint(100, 999))  # add three random digits to the end

        # even very unlikely, make sure username is not already taken
        if db.user.count_documents({'username': username}) == 0:
            do_generate = False

    return username


@router.get("/user/generate_username", response_model=str, description="Generate a new random username")
async def generate_username():
    return generate_or_get_username(None)


@router.get("/user/test_username/{username:path}",
            response_model=TestUsernameResult,
            description="Check if a username can be used for a new user")
async def test_username(username: str, current_user: User = Depends(session.currentUser)):
    if current_user.role != Role.Administrator and current_user.role != Role.Nurse:
        raise HTTPException(status_code=403, detail="Operation not permitted.")

    pattern = r'^[a-zA-Z0-9\+\-_#]+$'

    if not username or len(username) < 3 or not re.match(pattern, username):
        return TestUsernameResult.InvalidName
    if db.user.count_documents({'username': username}) > 0:
        return TestUsernameResult.UserExists

    return TestUsernameResult.OK


@router.post("/user", response_model=UserCreationResult)
async def create_user(user: UserInput, request: Request, current_user: User = Depends(session.currentUser)):
    # Ensure new user has not already an id attached, since it will be auto-generated
    if user.id:
        raise HTTPException(status_code=400, detail="A new user must not have an 'id' assigned.")

    return await upsert_user(user, request, current_user)


# @router.put("/user", response_model=LegacyResult)
# async def update_user(user: UserInput, request: Request, current_user: User = Depends(session.currentUser)):
#     # Ensure new user has not already an id attached, since it will be auto-generated
#     if not user.id:
#         raise HTTPException(status_code=400, detail="Missing user id for update.")
#
#     return await upsert_user(user, request, current_user)


# this request handler gets called by PUT /user (see below)
async def upsert_user(user: UserInput, request: Request, current_user: User = Depends(session.currentUser)) \
        -> UserCreationResult | LegacyResult:
    # Check if current user is allowed to create this type of new user:
    if user.role != Role.Participant.name:
        # only administrators may modify users with a different role than 'Participant'
        permitted = (current_user.role == Role.Administrator)
    else:
        # only administrators or nurses may modify users with the role 'Participant'
        permitted = (current_user.role == Role.Administrator or current_user.role == Role.Nurse)

    if not permitted:
        raise HTTPException(status_code=403, detail="Operation not permitted.")

    existing_user_data = None
    if user.id:
        existing_user_data = {'id': user.id}
        convertJSONToMongo(existing_user_data)
        existing_user_data = db.user.find_one(existing_user_data)
        if existing_user_data is None:
            raise HTTPException(status_code=404, detail="User does not exist.")

        if (current_user.study_id and existing_user_data['study_id'] != current_user.study_id
                and current_user.role != Role.Administrator):
            # current user is assigned to a study (should always be the case in newer version),
            # but the user to be modified is not assigned to this study
            raise HTTPException(status_code=403, detail="Operation not permitted.")

    user.username = user.username.strip()
    old_username = None
    if existing_user_data:
        # on update, check if username was changed
        old_username = existing_user_data['username']

        if user.username and old_username != user.username:
            new_username = user.username
        else:
            new_username = old_username
    else:
        # on insert, generate username if not set
        new_username = generate_or_get_username(user)

    if old_username != new_username:
        # New user or change username:
        test_username_result = await test_username(new_username, current_user)
        if test_username_result == TestUsernameResult.UserExists:
            raise HTTPException(status_code=409, detail="User with this username already exists")
        if test_username_result == TestUsernameResult.InvalidName:
            raise HTTPException(status_code=400, detail="Invalid username")

    user.username = new_username

    if user.email and not utils.is_valid_email(user.email):
        raise HTTPException(status_code=400, detail="Invalid email address.")

    new_password = user.new_password
    generated_password = False
    if not new_password and not existing_user_data:
        # auto-generate random password for new users, if not manually specified:
        new_password = ''.join(secrets.choice(string.ascii_letters + string.digits) for _ in range(8))
        generated_password = True

    user_data = to_mongo(user, exclude={"id", "new_password", "send_registration_mail", "email"})

    if new_password:
        # generate salt and password hash
        salt = secrets.token_urlsafe(16)
        password_hash = session.hashPassword(new_password, salt)

        # store hashed password in user object
        user_data['password_hash'] = password_hash
        user_data['salt'] = salt

    if not existing_user_data:
        # store creation date and creator id for new users
        user_data['created_by'] = current_user.id
        user_data['creation_date'] = datetime.utcnow()

        if current_user.study_id:
            # only set study id for users if current user is assigned to a study (for backwards compatibility)
            user_data['study_id'] = current_user.study_id

    # convert data for MongoDB
    convertJSONToMongo(user_data)

    if not existing_user_data:
        # store new User in database
        res = db.user.insert_one(user_data)
        user_data['_id'] = res.inserted_id
    else:
        res = db.user.update_one(existing_user_data, {"$set": user_data})
        user_data['_id'] = user.id

    if not res.acknowledged:
        raise HTTPException(status_code=500, detail="Unexpected error on modifying database.")

    # automatically generate login session, which can be used by client for QR code generation
    if user.email:
        auth_token = session.generateTokenForUser(user_data, current_user, permanent=True)
        # generate permanent session used for QR-Code sent by mail
        auth_token = auth_token['access_token']
        apk_download_url = utils.get_apk_url_with_token(request, auth_token)
        await send_participant_registration_email(request, UserBase(**user_data), user.email,
                                                  new_password,
                                                  apk_download_url)

    # respond to client
    if not existing_user_data:
        return UserCreationResult(id=str(user_data['_id']), username=user.username)
    else:
        return LegacyResult(success=True)


# The Android App currently uses the same endpoint for participants updating their own profile
# and for nurses updating/creating participants user's profile.
# For Participants, the app calls this endpoint with an object containing the user's id
# and the anamnesis_data only and lacks of a remaining fields UserInput object requires (role, username).
# For the server to be able to handle this, we introduced this intermediate handler, which however
# has an undefined input format and can therefore not provide a documented interface, but is able to handle both cases.
# TODO: Change the app to either use a separate endpoint or to provide a full UserInput object
# for that we can properly define the need of a UserInput object for this endpoint!
@router.put("/user", response_model=LegacyResult)
async def upsert_user_workaround(req: Request, current_user: User = Depends(session.currentUser)):
    ans = await req.json()

    if current_user.role == Role.Participant:
        # Participants can only update anamnesis_data for themselves

        if ans['id'] != str(current_user.id):
            raise HTTPException(status_code=403, detail="Operation not permitted.")
        if not ans['anamnesis_data']:
            raise HTTPException(status_code=400, detail="Missing participant profile data")

        res = db.user.update_one(
            {"_id": current_user.id},
            {"$set": {"anamnesis_data": ans["anamnesis_data"]}}
        )

        if not res.acknowledged:
            raise HTTPException(status_code=500, detail="Unexpected error on modifying database.")

        return LegacyResult(success=True)
    else:
        # Handle all other user types
        try:
            user_input = UserInput(**ans)  # Might raise a validation error
        except Exception as e:
            raise HTTPException(status_code=422, detail="Invalid input data: " + str(e))

        return await upsert_user(user_input, req, current_user)


@router.delete("/user/{user_id}", description="Delete a user", response_model=LegacyResult)
def delete_user(user_id: PyObjectId, current_user: User = Depends(session.currentUser)):
    user_to_delete = {"_id": user_id}

    if db.user.count_documents(user_to_delete) == 0:
        raise HTTPException(status_code=404, detail="User does not exist.")

    user_to_delete = db.user.find_one(user_to_delete)

    user_filter = {"user_id": user_to_delete["_id"]}

    if user_id == current_user.id:
        raise HTTPException(status_code=409, detail="You cannot delete yourself.")

    # Check if current user is allowed to delete this type of new user:
    if user_to_delete['role'] != Role.Participant.name:
        # only administrators may delete users with a different role than 'Participant'
        permitted = (current_user.role == Role.Administrator)
    else:
        # only administrators or nurses may delete users with the role 'Participant'
        permitted = (current_user.role == Role.Administrator or current_user.role == Role.Nurse)

    if (current_user.study_id and user_to_delete['study_id'] != current_user.study_id
            and current_user.role != Role.Administrator):
        # current user is assigned to a study (should always be the case in newer version),
        # but the user to be modified is not assigned to this study
        permitted = False

    if not permitted:
        raise HTTPException(status_code=403, detail="Operation not permitted.")

    res = db.user.delete_one(user_to_delete)
    if not res.acknowledged:
        raise HTTPException(status_code=500, detail="Unexpected error on deleting user.")

    error_str = ""

    res = db.sensor_data.delete_many(user_filter)
    if not res.acknowledged:
        error_str = error_str + "Database Error on deleting user's sensor data. "

    res = db.user_data.delete_many(user_filter)
    if not res.acknowledged:
        error_str = error_str + "Database Error on deleting user's question data. "

    res = db.session.delete_many(user_filter)
    if not res.acknowledged:
        error_str = error_str + "Database Error on deleting user's session data. "

    res = db.requests.delete_many({"$or": [{"request_user": user_to_delete["_id"]},
                                           {"target_user": user_to_delete["_id"]}]})
    if not res.acknowledged:
        error_str = error_str + "Database Error on deleting user's requests. "

    res = db.syncs.delete_many(user_filter)
    if not res.acknowledged:
        error_str = error_str + "Database Error on deleting user's synchronization log. "

    if error_str:
        raise HTTPException(
            status_code=500,
            detail="User deleted successfully, but other errors occurred: " + error_str
        )

    return LegacyResult(success=True)


# noinspection PyUnusedLocal
@router.get("/logout", description="Logout current user", response_model=LegacyResult, tags=["Session Management"])
def logout(res=Depends(session.destroyToken)):
    return LegacyResult(success=res)


@router.get("/user/{user_id}", description="Get user information for specific user", response_model=LegacyUserOutput)
def get_user(user_id: PyObjectId, current_user: User = Depends(session.currentUser)):
    # Check if user with the specified id exists in database
    target_user = {'_id': user_id}
    target_user_data = db.user.find_one(target_user)

    if target_user_data is None:
        raise HTTPException(status_code=404, detail="User does not exist.")

    del target_user_data['password_hash']
    del target_user_data['salt']

    target_user = UserOutput(**target_user_data)

    permitted = False

    if current_user.role == Role.Administrator:
        permitted = True

    if current_user.role == Role.Supervisor and current_user.study_id:
        # Researchers are only allowed to view other user's information if they are assigned to the same study
        if current_user.study_id == target_user.study_id:
            permitted = True

    if (current_user.role == Role.Nurse and target_user.role == Role.Participant and
            (not current_user.study_id or current_user.study_id == target_user.study_id)
    ):
        # Nurses are only allowed to view other user's information if they are assigned to the same study
        permitted = True

    if target_user.id == current_user.id:
        # users may view their own information
        permitted = True

    if not permitted:
        raise HTTPException(status_code=403, detail="Operation not permitted.")

    if current_user.role == Role.Supervisor and current_user.username != target_user.username:
        # Researchers may not access usernames, they may only see IDs
        target_user.username = ""

    return LegacyUserOutput(user=target_user)


@router.get("/staff/{study_id}",
            description="Get list of all staff members (nurse or scientist profiles) assigned to a specific study.",
            response_model=List[UserOutput])
def list_users(study_id: PyObjectId, current_user: User = Depends(session.currentUser)):
    if current_user.role != Role.Administrator:
        # only Administrators are allowed to list staff members
        raise HTTPException(status_code=403, detail="Operation not permitted.")

    staff = []
    for user in db.user.find({'study_id': study_id, 'role': {'$in': [Role.Supervisor.name, Role.Nurse.name]}}):
        user = UserOutput(**user)
        staff.append(user)

    return staff


@router.get("/users", description="Get list of all users (for Administrators) or "
                                  "participants (for Nurses and Scientist)",
            response_model=LegacyUsersOutput)
def list_users(current_user: User = Depends(session.currentUser)):
    search_filter = {"role": Role.Participant.name}

    if current_user.role == Role.Administrator:
        # only Administrators are allowed to list other users than participants
        del search_filter["role"]

    if current_user.role == Role.Participant:
        raise HTTPException(status_code=403, detail="Operation not permitted.")

    if (current_user.role == Role.Nurse or current_user.role == Role.Supervisor) and current_user.study_id:
        # Nurses are only allowed to view other user's information if they are assigned to the same study
        search_filter['study_id'] = current_user.study_id

    users = []
    for user in db.user.find(search_filter):

        # remove security-relevant information from User object
        del user['password_hash']
        del user['salt']

        user = UserOutput(**user)

        if current_user.role == Role.Supervisor:
            # Researchers may not access usernames, they may only see IDs
            user.username = ""

        users.append(user)

    return LegacyUsersOutput(users=users)


@router.post("/sendmail/{user_id}", description="Re-Send registration mail to user", response_model=LegacyResult)
async def send_mail_request(user_id: PyObjectId, params: ResendMailRequest, request: Request,
                            current_user: User = Depends(session.currentUser)):
    if not params.email or not utils.is_valid_email(params.email):
        raise HTTPException(status_code=400, detail="Invalid email address.")

    target_user_filter = {'_id': user_id}

    permitted = False
    # Either Administrators or Nurses are allowed to trigger sending new registration mail
    # Or other users for their own account
    if (current_user.role == Role.Administrator
            or current_user.role == Role.Nurse
            or current_user.id == user_id):
        permitted = True

    if not permitted:
        raise HTTPException(status_code=403, detail="Operation not permitted.")

    target_user_data = db.user.find_one(target_user_filter)
    if target_user_data is None:
        raise HTTPException(status_code=404, detail="User does not exist.")

    target_user = UserBase(**target_user_data)

    if current_user.role == Role.Nurse and current_user.study_id:
        # Nurses are only allowed to view other user's information if they are assigned to the same study
        if current_user.study_id != target_user.study_id:
            raise HTTPException(status_code=403, detail="Operation not permitted.")

    password = None
    user_mod = None

    if params.reset_password:
        # generate new random Password and salt
        salt = secrets.token_urlsafe(16)
        password = ''.join(secrets.choice(string.ascii_letters + string.digits) for _ in range(8))

        # generate new hashed password
        password_hash = session.hashPassword(password, salt)

        user_mod = {'salt': salt, 'password_hash': password_hash}

    # generate permanent session used for QR-Code sent by mail
    auth_token = session.generateTokenForUser(target_user_data, current_user, permanent=True)
    auth_token = auth_token['access_token']
    apk_download_url = utils.get_apk_url_with_token(request, auth_token)

    # send registration mail
    success = await send_participant_registration_email(request, target_user, params.email, password, apk_download_url)

    if success and user_mod:
        # store new Password for user in database only if mail was sent successfully
        db.user.update_one(target_user_data, {"$set": user_mod})

    return {'success': success}


@router.get("/me", description="Get information about current user", response_model=UserOutput)
def get_current_user(current_user: User = Depends(session.currentUser)):
    current_user_resp = get_user(current_user.id, current_user)

    return current_user_resp.user


def update_user_client_version(user: User, version: int):
    if not user or version < 1:
        return

    if user.client_version == version:
        return  # already correct client version

    target_user = {'_id': user.id}
    target_user = db.user.find_one(target_user)
    if target_user is None:
        return

    db.user.update_one(target_user, {"$set": {'client_version': version}})
