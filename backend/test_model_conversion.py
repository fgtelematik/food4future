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

from schema_v2.schema_models import FoodEnum, FoodEnumTransition
from models import PyObjectId, to_mongo, UserOutput, Role, db
from datetime import datetime

# Test proper nested ObjectID conversions
foodenum_obj = FoodEnum(
    identifier="test",
    label={"en": "Test", "de": "Test"},
    item_ids=[PyObjectId("650beb267192cdc3642541e5"), PyObjectId("650bebbced95ec1edec7718b")],
    transitions=[
        FoodEnumTransition(
            require_tags=[],
            add_tags=["added_tag"],
            selected_item_id=None,
            target_enum=PyObjectId("650beb97748510fc83e6d676")
        )
    ]
)

foodenum_dict= foodenum_obj.model_dump()
foodenum_mongo = to_mongo(foodenum_obj)

print(foodenum_obj)
print(foodenum_dict)
print(foodenum_mongo)

#db.test_conv.insert_one(foodenum_mongo)

# ============================================================================
print("----------")

# Test proper date and other conversions
user_obj = UserOutput(
    id=PyObjectId("650beb267192cdc3642541e5"),
    username="testuser",
    role=Role.Participant,
    created_by=PyObjectId("650bee4c3595d51d1993cee6"),
    creation_date=datetime.utcnow(),
    client_version=10,
    anamnesis_data={
        "age": 30,
        "occupation": None,
    },
    study_id=PyObjectId("650beec52b40618aa26844ad"),
)

user_dict = user_obj.model_dump()
user_mongo = to_mongo(user_obj)

print(user_obj)
print(user_dict)
print(user_mongo)

# db.test_conv.insert_one(user_mongo)

