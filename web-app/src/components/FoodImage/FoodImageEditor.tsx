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

import './FoodImage.css'
import {FoodImage} from "../../models/main.ts";
import {FC, useState} from "react";
import {Button, TextField} from "@mui/material";
import {locStr} from "../../utils.ts";
import {EditorDialog} from "../Editor/EditorDialog.tsx";
import useApi from "../../hooks/useApi";
import {useDataState} from "../../hooks/useState/useData.ts";
import {AutocompleteEditField} from "../Editor/AutocompleteEditField.tsx";
import {EditorProps} from "../generics.ts";

export const FoodImageEditor : FC<EditorProps<FoodImage>> = ({editItem, setEditItem, onSaveItem}) => {
	
	const foodImages = useDataState(state => state.foodImages);
	const [autoselectedLicenseUrl, setAutoselectedLicenseUrl] = useState<boolean>(false);
	const onConfirm = async () => {
		if(!editItem)
			return;
		
		const success = await onSaveItem(editItem);
		if(success)
			setEditItem(null);
	}
	
	const onImageFileChange = (e: any) => {
		if(!editItem)
			return;

		if(!e.target.files || e.target.files.length === 0)
			setEditItem({...editItem, filename : undefined});

		setEditItem({...editItem, filename : e.target.files[0]});
	}

	let previewImageUrl = "";
	let uploadFilename = "";
	if(typeof(editItem?.filename) === "string" && editItem?.filename !== "")
		previewImageUrl = useApi().getImageUrl(editItem.filename)
	else if (editItem?.filename instanceof File) {
		uploadFilename = editItem.filename.name;
		previewImageUrl = URL.createObjectURL(editItem.filename);
	}

	const isValidInput =
		!!editItem &&
		!!editItem.label && !!editItem.filename

	
	const onLicenseChange = (value: string | undefined) => {
		if(!editItem)
			return;
		
		const addLicenseUrl : Pick<FoodImage, "licenseUrl"> = {};

		const existingUrl = foodImages.find(fi => fi.licenseName === value)?.licenseUrl;

		if(existingUrl && (!editItem.licenseUrl  || autoselectedLicenseUrl)) {
			addLicenseUrl["licenseUrl"] = existingUrl;
			setAutoselectedLicenseUrl(true);
		} else 
			setAutoselectedLicenseUrl(false);
		
		setEditItem({...editItem, licenseName: value, ...addLicenseUrl});
	}
	

	return (
		<EditorDialog
			open={editItem !== null}
			title={(!!editItem?.id ? "Edit" : "Create") + " Food Image"}
			onSubmit={() => onConfirm()}
			onCancel={() => setEditItem(null)}
			isValidContent={isValidInput}
		>
			<div className={"food-image-editor-container"}>
				<div className={"food-image-editor-fields"}>
					<TextField
						label={"Image Label*"} value={editItem?.label ?? ""}
						onChange={(e) => setEditItem({...editItem!, label: e.target.value})}/>
					
					<AutocompleteEditField
						label={"Author / Source Info"} value={editItem?.sourceInfo ?? ""}
						onChange={(value) => setEditItem({...editItem!, sourceInfo: value})}
						optionsSource={foodImages.map(fi=>fi.sourceInfo)}/>
					
					<TextField
						label={"Source URL"} value={editItem?.sourceUrl ?? ""} type="url"
						onChange={(e) => setEditItem({...editItem!, sourceUrl: e.target.value})}/>
					
					<AutocompleteEditField
						label={"License Name"} value={editItem?.licenseName ?? ""}
						onChange={onLicenseChange}
						optionsSource={foodImages.map(fi=>fi.licenseName)}/>
					
					<AutocompleteEditField
						label={"License URL"} value={editItem?.licenseUrl ?? ""}  type="url"
						onChange={(value) => setEditItem({...editItem!, licenseUrl: value})}
						optionsSource={foodImages.map((fi)=>fi.licenseUrl)}/>
					
				</div>
				<div className={"food-image-editor-preview"}>
					{!previewImageUrl && <i>Please pick an image file</i>}
					{previewImageUrl && <img className={"preview-image"} src={previewImageUrl}
											 alt={locStr(editItem?.label) ?? ""}/>}
					<i>{uploadFilename}</i>
					<Button
						variant="contained"
						component="label"
					> Browse...
						<input
							type="file"
							accept={"image/png, image/jpeg"}
							hidden
							onChange={onImageFileChange}
						/></Button>
					<p>Please note the image requires a fixed resolution of <b>375 x 200</b> pixels.</p>
				</div>
			</div>
		</EditorDialog>
	)
}