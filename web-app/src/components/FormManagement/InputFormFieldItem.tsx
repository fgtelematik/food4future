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
import {IconButton} from "@mui/material";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";

import DeleteIcon from '@mui/icons-material/Delete';
import {useDataState} from "../../hooks/useState/useData.ts";
import {FoodEnumItem} from "../../models/main.ts";
import {InputFormFieldItemPreview} from "./InputFormFieldItemPreview.tsx";
import {InputField} from "../../models/form.ts";

type InputFormFieldItemViewProps = {
    item_id: string | null;
    index?: number;
    onChange: (new_id: string) => void;
    onMoveUp?: () => void;
    onMoveDown?: () => void;
    onDelete?: () => void;
    className?: string;
    options: InputField[];
    errorText?: string;
    action?: "add" | "edit",
    onAction?: () => void,
}


export const InputFormFieldItem: FC<InputFormFieldItemViewProps> =
    ({
         item_id,
         index,
         onChange,
         onMoveUp,
         onMoveDown,
         onDelete,
         className,
         options,
         errorText,
        action,
        onAction

     }) => {
        const currentItem = item_id == null ? undefined : options.find(field => field.id === item_id);

        const [fieldNotFoundErrorText, setFieldNotFoundErrorText] = useState<string | undefined>();

        useEffect(
            () => {
                setFieldNotFoundErrorText(item_id != null && currentItem == undefined ? "Form Field not found" : undefined);
            }, [item_id, currentItem]
        )

        const changeItem = (item: FoodEnumItem | undefined) => {
            setFieldNotFoundErrorText(undefined);
            onChange(item!.id!);
        }

        return (
            <div className={className ? className : "enum-item"}>
                <InputFormFieldItemPreview errorText={errorText || fieldNotFoundErrorText}
                                           label={item_id == null ? "Add Form Field ..." : ("Field" + (index != undefined ? " " + (index + 1) : ""))}
                                           value={currentItem} onChange={changeItem} items={options}
                                           action={action} onAction={onAction}
                />
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