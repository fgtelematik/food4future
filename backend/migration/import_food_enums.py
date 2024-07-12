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

from models import db
from schema_v2.schema_models import FoodEnum, FoodImage, FoodEnumItem, FoodEnumTransition


def create_image(path: str, label: str) -> str | None:
    source_info = path.split("/")[-1].split(".")[0]
    filename = path.replace("images/", "")
    image = FoodImage(
        filename=filename,
        label=label,
        sourceInfo=source_info
    )

    existing_image = db.food_images.find_one({"filename": filename})

    if existing_image:
        print("Warning: Image with filename %s already exists in database." % filename)
        return existing_image["_id"]

    return db.food_images.insert_one(image.dict(exclude={"id"})).inserted_id


def create_items(ids: list[str], labels: list[str], image_urls: list[str] | None, has_food_items: bool) -> list[str]:
    res = []

    for i in range(len(ids)):
        existing_item = db.food_enum_items.find_one({"identifier": ids[i]})
        if existing_item:
            print("Warning: Item with identifier %s already exists in database." % ids[i])
            res.append(existing_item["_id"])
            continue

        image_id = None
        if image_urls and image_urls[i]:
            image_id = create_image(image_urls[i], labels[i])

        item = FoodEnumItem(
            identifier=ids[i],
            label=labels[i],
            image=image_id,
            is_food_item=has_food_items
        )

        new_id = db.food_enum_items.insert_one(item.dict(exclude={"id"})).inserted_id
        res.append(new_id)

    return res


def convert_transitions(transitions: dict) -> list[FoodEnumTransition]:
    transition_keys = list(transitions.keys())
    res = []
    for key_string in transition_keys:
        parts = key_string.split("+")
        source_item = parts[0]
        require_tags = parts[1:]

        if source_item == "*":
            source_item = None
        else:
            source_item = db.food_enum_items.find_one({"identifier": source_item})
            if not source_item:
                print("Warning: Source item with identifier %s does not exist in database." % source_item)
                continue
            source_item = source_item["_id"]

        if transitions[key_string] is None:
            target_enum = None
            add_tags = []
        else:
            parts = transitions[key_string].split("+")
            target_enum = parts[0]
            add_tags = parts[1:]

        if target_enum:
            target_enum = db.food_enums.find_one({"identifier": target_enum})
            if not target_enum:
                print("Warning: Target enum with identifier %s does not exist in database." % target_enum)
                continue
            target_enum = target_enum["_id"]

        res.append(FoodEnumTransition(
            require_tags=require_tags,
            add_tags=add_tags,
            selected_item_id=source_item,
            target_enum=target_enum
        ))

    return res


def main():

    enum_file = open("schema/enums_old.json", 'r', encoding='utf8')
    enum_data = json.load(enum_file)

    enums: list = enum_data["enums"]
    all_transitions: dict = enum_data["enum_transitions"]

    # Delete all non-food enums (which are currently all the ones before "FoodType"
    while len(enums) > 0:
        enum = enums[0]
        if enum["id"] == "FoodType":
            break

        enums.remove(enum)

    enum_ids = []

    # First create all images, then create all items, then create all enums
    for enum in enums:
        id = enum["id"]

        print("Creating enum %s" % id)

        label = enum["label"] if "label" in enum else None
        help_text = enum["helpText"] if "helpText" in enum else None
        contains_food_items = enum["contains_food_items"] if "contains_food_items" in enum else False

        existing_enum = db.food_enums.find_one({"identifier": id})
        if existing_enum:
            print("Warning: Enum with identifier %s already exists in database." % id)
            enum_ids.append(existing_enum["_id"])
            continue

        item_ids = create_items(
            enum["element_ids"],
            enum["element_labels"],
            enum["element_image_urls"] if "element_image_urls" in enum else None,
            contains_food_items)

        enum_obj = FoodEnum(
            identifier=id,
            label=label,
            help_text=help_text,
            item_ids=item_ids,
            transitions=[]
        )

        new_id = db.food_enums.insert_one(enum_obj.dict(exclude={"id"})).inserted_id
        enum_ids.append(new_id)

    # Now create all transitions
    for enum in enums:
        id = enum["id"]
        if id not in all_transitions:
            continue

        print("Creating transitions for enum %s" % id)

        transitions = convert_transitions(all_transitions[id])
        transitions_as_dicts = [t.dict(exclude={"id"}) for t in transitions]

        enum_obj = db.food_enums.find_one({"identifier": id})
        if not enum_obj:
            print("Warning: Enum with identifier %s does not exist in database." % id)
            continue

        db.food_enums.update_one({"_id": enum_obj["_id"]}, {"$set": {"transitions": transitions_as_dicts}})


if __name__ == "__main__":
    main()
