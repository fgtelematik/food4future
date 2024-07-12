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

import {IconButton, Paper} from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import {FC} from "react";
import {EntryProps} from "../generics.ts";
import {InputForm} from "../../models/form.ts";
import EditNoteIcon from '@mui/icons-material/EditNote';
import {useDataState} from "../../hooks/useState/useData.ts";
import {locStr} from "../../utils.ts";

export const InputFormEntry: FC<EntryProps<InputForm>> = ({item, onEdit, onDelete}) => {
    const allInputFields = useDataState(state => state.inputFields);
    let fieldLst = item.fields.map(field => allInputFields.find(f => f.id === field));
    fieldLst = fieldLst.filter(field => field != undefined);
    const fieldListStr = fieldLst.map(field => field?.identifier).join(" | ");

    return (
        <Paper className={"entry-container"}>
            <div className={"entry-column"}  onClick={() => onEdit(item)}>
                <div className={"no-image-container"}><EditNoteIcon /></div>
            </div>
            <div className={"entry-column entry-column2 "}  onClick={() => onEdit(item)}>
                <h3>{locStr(item.title) || <i>Untitled Form</i>}</h3>
            <span className={"additional-info"}>{item.identifier}</span><br/><br/>
                <p>Fields: <span className={"additional-info"}>{fieldListStr || <i>None</i>}</span></p>

            </div>
            <div className={"entry-column "}>
                <IconButton aria-label={"Delete"} onClick={() => onDelete(item)}><DeleteIcon/></IconButton>
            </div>
        </Paper>
    )
}