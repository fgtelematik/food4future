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

import {FoodImage} from "../../models/main.ts";
import {FC} from "react";
import "./FoodImage.css";
import {IconButton, Paper} from "@mui/material";
import useApi from "../../hooks/useApi";
import {locStr} from "../../utils.ts";
import DeleteIcon from '@mui/icons-material/Delete';
import {EntryProps} from "../generics.ts";

export const FoodImageEntry:FC<EntryProps<FoodImage>> = ({item, onEdit, onDelete}) => {
	
	let previewImageUrl = "";
	if(typeof(item.filename) === "string" && item.filename !== "")
		previewImageUrl = useApi().getImageUrl(item.filename)
	
	const label = locStr(item.label) ?? "";
	
	return (
		<Paper className={"entry-container"} elevation={1}>
			<div className={"entry-column entry-column1"} onClick={() => onEdit(item)}>
				<img src={previewImageUrl} alt={label} className={"preview-image"}/>
			</div>
			<div className={"entry-column entry-column2"} onClick={() => onEdit(item)}>
				<h3>{label} </h3>
				<p>by <i>{item.sourceInfo ?? "(Unknown Author)"}</i></p><p>&nbsp;</p>
				<p>License: <i>{item.licenseName ?? "(Unknown License)"}</i></p>
				<p>
					{item.sourceUrl && <a href={item.sourceUrl} target={"_blank"} rel={"noreferrer"}>Go to source</a>}
					{item.licenseUrl && item.sourceUrl && <span> | </span>}
					{item.licenseUrl && <a href={item.licenseUrl} target={"_blank"} rel={"noreferrer"}>Read license</a>}
						   </p>
			</div>
			<div className={"entry-column entry-column3"}>
				<IconButton aria-label={"Delete"} onClick={() => onDelete(item)}><DeleteIcon/></IconButton>
			</div>
		</Paper>
	)
}