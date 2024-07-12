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

import useApi from "../../hooks/useApi";
import {locStr} from "../../utils.ts";
import {IconButton, Paper} from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import {FoodEnumItem} from "../../models/main.ts";
import {FC} from "react";
import HideImageIcon from '@mui/icons-material/HideImage';
import {useDataState} from "../../hooks/useState/useData.ts";
import {EntryProps} from "../generics.ts";

export const FoodItemEntry: FC<EntryProps<FoodEnumItem>> = ({item, onEdit, onDelete}) => {
    let previewImageUrl = "";
    if (typeof (item.image?.filename) === "string" && item.image.filename !== "")
        previewImageUrl = useApi().getImageUrl(item.image.filename)

    const label = locStr(item.label) ?? "";
    
    const foodEnums = useDataState(state => state.foodEnums);
    
    const screenList = foodEnums.filter(e => item?.id && e.item_ids?.includes(item.id)).map(e => e.identifier);
    const screenWord = screenList.length === 1 ? "Food Screen" : "Food Screens";
    
    
    return (
        <Paper className={"entry-container"}>
            <div className={"entry-column"}  onClick={() => onEdit(item)}>{
                previewImageUrl ?
                <img src={previewImageUrl} alt={label} className={"preview-image-small"}/>
                    :
                <div className={"no-image-container"}><HideImageIcon /></div>
                
            }
            </div>
            <div className={"entry-column entry-column2 "}  onClick={() => onEdit(item)}>

                <h3>{label}</h3>
                <p className={"additional-info"}>{item.identifier}</p>
                <p>{screenList && screenList.length > 0 && (
                    <span><br/>used in {screenWord}: <i>{screenList.join(", ")}</i></span>
                )}</p>
            
            </div>
            <div className={"entry-column "}>
                <IconButton aria-label={"Delete"} onClick={() => onDelete(item)}><DeleteIcon/></IconButton>
            </div>
        </Paper>
    )
}