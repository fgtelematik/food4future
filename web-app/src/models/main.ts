/*
 * f4f Study Management Portal (Web App)
 *
 * Copyright (c) 2024 Technical University of Applied Sciences Wildau
 * Author: Philipp Wagner, Research Group Telematics
 * Contact: fgtelematik@th-wildau.de
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation version 2.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

export type ObjectId = string;

/**
 * A type that can be used to represent a string that can be localized.
 */
export type LocalizedStr = string | { [language_key: string]: string };

export type FoodImage = {
    id?: ObjectId
    filename?: string | File
    label?: LocalizedStr
    licenseUrl?: string
    licenseName?: string
    sourceInfo?: string
    sourceUrl?: string
}

export type FoodEnumItem = {
    id?: ObjectId
    identifier?: string
    label?: LocalizedStr
    explicit_label?: LocalizedStr    
    image?: FoodImage
    is_food_item?: boolean
}

export type FoodEnumTransition = {
    require_tags: string[]
    add_tags: string[]
    target_enum?: ObjectId
    selected_item_id?: ObjectId
}

export type FoodEnum = {
    id?: ObjectId
    identifier?: string
    label?: LocalizedStr
    help_text?: LocalizedStr
    item_ids: ObjectId[]
    transitions: FoodEnumTransition[]
}