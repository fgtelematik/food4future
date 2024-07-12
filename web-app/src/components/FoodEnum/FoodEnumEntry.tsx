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

import {FoodEnum} from "../../models/main.ts";
import {FC} from "react";
import {IconButton, Paper, Tooltip} from "@mui/material";
import {locStr} from "../../utils.ts";
import DeleteIcon from '@mui/icons-material/Delete';
import {useDataState} from "../../hooks/useState/useData.ts";
import useApi from "../../hooks/useApi";
import "./FoodEnum.css"
import {EntryProps} from "../generics.ts";


export const FoodEnumEntry:FC<EntryProps<FoodEnum>> = ({item, onEdit, onDelete}) => {
	//
	// let previewImageUrl = "";
	// if(typeof(foodImage.filename) === "string" && foodImage.filename !== "")
	// 	previewImageUrl = useApi().getImageUrl(foodImage.filename)
	//
	// const label = locStr(foodImage.label) ?? "";
	
	const items = useDataState(state => state.foodItems);

	const previewImages = item.item_ids.map((item_id) => {
		const item = items.find(item => item.id === item_id);
		if (!item || !item.image || typeof(item.image.filename) !== "string")
			return null;
		
		const previewImageUrl = useApi().getImageUrl(item.image.filename)
		
		return (
			<Tooltip title={locStr(item.label) ?? ""} key={item.id}>
				<img src={previewImageUrl} alt={locStr(item.label) ?? ""} className={"mini-image"}/>
			</Tooltip>
		)
	});
		
	
	return (
		<Paper className={"entry-container"} elevation={1}>
			<div className={"entry-column entry-column1"} onClick={() => onEdit(item)}>
				{/*<img src={previewImageUrl} alt={label} className={"preview-image"}/>*/}
				<h3>{locStr(item.label)}</h3>
				<p className={"additional-info"}>{item.identifier}</p>
			</div>

			<div className={"entry-column entry-column2"} onClick={() => onEdit(item)}>
				{/*<img src={previewImageUrl} alt={label} className={"preview-image"}/>*/}
				<p>{previewImages}</p>
			</div>

			<div className={"entry-column entry-column3"}>
				<IconButton aria-label={"Delete"} onClick={() => onDelete(item)}><DeleteIcon/></IconButton>
			</div>
		</Paper>
	)
}