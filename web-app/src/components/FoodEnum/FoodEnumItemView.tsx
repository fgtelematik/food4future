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

import {FC, useEffect, useState} from "react";
import {FoodItemPreviewField} from "./FoodItemPreviewField.tsx";
import {IconButton} from "@mui/material";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";

import DeleteIcon from '@mui/icons-material/Delete';
import {useDataState} from "../../hooks/useState/useData.ts";
import {FoodEnumItem} from "../../models/main.ts";

type FoodEnumItemViewProps = {
    item_id: string | null;
    index?: number;
    onChange: (new_id: string) => void;
    onMoveUp?: () => void;
    onMoveDown?: () => void;
    onDelete?: () => void;
    className?: string;
}


export const FoodEnumItemView: FC<FoodEnumItemViewProps> =
    ({
         item_id,
         index,
         onChange,
         onMoveUp,
         onMoveDown,
         onDelete,
         className
     }) => {

        const foodItems = useDataState(state => state.foodItems);
        const currentItem = item_id == null ? undefined : foodItems.find(fi => fi.id === item_id);

        const [errorText, setErrorText] = useState<string | undefined>();

        useEffect(
            () => {
                setErrorText(item_id != null && currentItem == undefined ? "Food Item not found" : undefined);
            }, [item_id, currentItem]
        )

        const changeItem = (item: FoodEnumItem | undefined) => {
            setErrorText(undefined);
            onChange(item!.id!);
        }

        return (
            <div className={className ? className : "enum-item"}>
                <FoodItemPreviewField errorText={errorText}
                                      label={item_id == null ? "Add Item ..." : ("Food Item" + (index != undefined ? " " + (index + 1) : ""))}
                                      value={currentItem} onChange={changeItem} items={foodItems}/>
                {item_id && <>
                    <IconButton disabled={!onDelete} onClick={() => onDelete && onDelete()}><DeleteIcon
                        fontSize={"small"}/></IconButton>
                    <IconButton disabled={!onMoveUp} onClick={() => onMoveUp && onMoveUp()}><ArrowUpwardIcon
                        fontSize={"small"}/></IconButton>
                    <IconButton disabled={!onMoveDown} onClick={() => onMoveDown && onMoveDown()}><ArrowDownwardIcon
                        fontSize={"small"}/></IconButton>
                </>
                }
            </div>
        )
    }