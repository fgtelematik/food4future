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

from enum import Enum


class Role(str, Enum):
    Administrator = 'Administrator'
    Nurse = 'Nurse'
    Supervisor = 'Supervisor'
    Participant = 'Participant'


class DataType(str, Enum):
    UserData = 'UserData'
    LabData = 'LabData' # TODO: Remove this.
                        # Lab Data be included in StaticData (e.g. via ADT ListType fields) set to
                        # writable for staff only
    StaticData = 'StaticData'
    SensorData = 'SensorData'


class LogInMode(str, Enum):
    Credentials = 'Credentials'
    AppGeneratedToken = 'AppGeneratedToken'
    EMailToken = 'EMailToken'


class TestUsernameResult(str, Enum):
    OK = 'OK'
    UserExists = 'UserExists'
    InvalidName = 'InvalidName'
