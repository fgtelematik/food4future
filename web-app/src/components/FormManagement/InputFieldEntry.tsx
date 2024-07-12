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

import {locStr} from "../../utils.ts";
import {IconButton, Paper} from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import {FC} from "react";
import {EntryProps} from "../generics.ts";
import {InputField} from "../../models/form.ts";
import CreateIcon from '@mui/icons-material/Create';
import {useGetFullInputFieldTypeLabel} from "../../hooks/other_hooks.tsx";
import {useDataState} from "../../hooks/useState/useData.ts";

export const InputFieldEntry: FC<EntryProps<InputField>> = ({item, onEdit, onDelete}) => {
    const inputForms = useDataState(state => state.inputForms);

    const label = locStr(item.label) ?? "";

    const fieldTypeLabel = useGetFullInputFieldTypeLabel(item);

    const formList = inputForms.filter(e => item?.id && e.fields?.includes(item.id)).map(e => e.identifier);
    const formWord = formList.length === 1 ? "Form" : "Forms";

    return (
        <Paper className={"entry-container"}>
            <div className={"entry-column"}  onClick={() => onEdit(item)}>
                    <div className={"no-image-container"}><CreateIcon /></div>
            </div>
            <div className={"entry-column entry-column1 "}  onClick={() => onEdit(item)}>

                <h3>{label}</h3>
                <p className={"additional-info"}>{item.identifier}</p>
                <p>{formList && formList.length > 0 && (
                    <span><br/>used in {formWord}: <i>{formList.join(", ")}</i></span>
                )}</p>
            </div>
            <div className={"entry-column entry-column2 "}  onClick={() => onEdit(item)}>
                <p>{fieldTypeLabel}</p>
            </div>
            <div className={"entry-column entry-column2 "}  onClick={() => onEdit(item)}>
                {!!item.helpText && <p className={"additional-info"}>{locStr(item.helpText)}</p>}
            </div>

            <div className={"entry-column "}>
                <IconButton aria-label={"Delete"} onClick={() => onDelete(item)}><DeleteIcon/></IconButton>
            </div>
        </Paper>
    )
}