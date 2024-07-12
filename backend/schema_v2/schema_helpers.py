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

from fastapi import Depends

from routers import session
from models import PyObjectId


def get_food_enums_for_study(study_id: PyObjectId):
    # TODO: Implement
    return []


def get_food_enum_items_for_study(study_id: PyObjectId):
    # TODO: Implement
    return []


def get_legacy_schemas(current_user=Depends(session.currentUser)):
    # TODO: Implement
    return {"schemas": []}

def get_legacy_enums(current_user=Depends(session.currentUser)):
    # TODO: Implement
    return {"enums": []}

def get_legacy_adts(current_user=Depends(session.currentUser)):
    # TODO: Implement
    return {}

