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

import {FoodEnumItem, FoodImage} from "../../models/main.ts";
import {FC, useEffect, useState} from "react";
import {FormControl, FormControlLabel, FormLabel, LinearProgress, Radio, RadioGroup, TextField} from "@mui/material";
import {capitalizeFirstLetter, locStr} from "../../utils.ts";
import {EditorDialog} from "../Editor/EditorDialog.tsx";
import useApi from "../../hooks/useApi";
import {useDataState} from "../../hooks/useState/useData.ts";

import "../FoodImage/FoodImage.css";
import {ImagePreviewField} from "./ImagePreviewField.tsx";
import {FoodImageEditor} from "../FoodImage/FoodImageEditor.tsx";
import {EditorProps} from "../generics.ts";

export const FoodItemEditor : FC<EditorProps<FoodEnumItem>> = ({editItem, setEditItem, onSaveItem}) => {

	const foodItems = useDataState(state => state.foodItems);
	const [newFoodImage, setNewFoodImage] = useState<FoodImage | null>(null);
	const [uploadingImage, setUploadingImage] = useState(false);
	
	const onConfirm = async () => {
		if(!editItem)
			return;

		const success = await onSaveItem(editItem);
		if(success)
			setEditItem(null);
	}

	const setImage = (image: FoodImage | undefined) => {
		if(!editItem)
			return;
		
		if(!image) {
			setEditItem({...editItem, image : undefined});
			return;
		}

		const values : FoodEnumItem = {image};

		const imgLabel = locStr(image.label) ?? "";
		
		if(!editItem.label)
			values.label = imgLabel;
		if(!editItem.identifier)
			values.identifier = imgLabel.toLowerCase();

		setEditItem({...editItem!, ...values})
	}
	
	const  onSaveNewFoodImage = async (image: FoodImage) =>  {
		if(!editItem)
			return false;
		
		let uploadedImage : FoodImage;
		
		setUploadingImage(true);
		try {
			uploadedImage = await useApi().upsertImage(image);
			
			setImage(uploadedImage);
			
			return true;
		} finally {
			setUploadingImage(false);
		}
		
		return false;
	}
	
	

	let previewImageUrl = "";
	
	
	const imageFilename = editItem?.image?.filename;
	
	if(typeof(imageFilename) === "string" && imageFilename !== "")
		previewImageUrl = useApi().getImageUrl(imageFilename);
	
	const identifierAlreadyExists = !!editItem && foodItems.some(fi=>fi.identifier === editItem.identifier && fi.id !== editItem.id);

	const isValidInput =
		!!editItem && !!editItem.label && !!editItem.identifier && !identifierAlreadyExists;
	

	useEffect( () => {
		useApi().fetchImages()
	}, []);
	
	const allImages = useDataState(state => state.foodImages);
	
	return (
		<>
			
			<EditorDialog
			open={editItem !== null}
			title={(!!editItem?.id ? "Edit" : "Create") + " Food Item"}
			onSubmit={() => onConfirm()}
			onCancel={() => setEditItem(null)}
			isValidContent={isValidInput}
		>
			<div className={"food-image-editor-container"}>
				<div className={"food-image-editor-fields"}>
					
					<TextField
						label={"Data Identifier*"} value={editItem?.identifier ?? ""}
						error={identifierAlreadyExists} helperText={identifierAlreadyExists ? "There is already a food item with this identifier" : undefined}
						onChange={(e) => setEditItem({...editItem!, identifier: e.target.value.toLowerCase()})}/>
					
					<TextField
						label={"Item Label*"} value={editItem?.label ?? ""} 
						onChange={(e) => setEditItem({...editItem!, label: e.target.value})}/>
					
					{ editItem?.is_food_item &&
						<TextField
							label={"Explicit Label (Optional)"}
							helperText={"This label should be added, if the item label does not explicitly describe the food item."}
							value={editItem?.explicit_label ?? ""}
							placeholder={locStr(editItem?.label)}
							onChange={(e) => setEditItem({...editItem!, explicit_label: e.target.value ? e.target.value : undefined})}/>
					}
					
					<ImagePreviewField
						images={allImages}
						label={"Image"} value={editItem?.image}
						onAddNew={() => setNewFoodImage( {label: editItem?.label ? editItem.label : ( editItem?.identifier ? capitalizeFirstLetter(editItem.identifier) : "")})} 
						onChange={(value) => {
							setImage(value);
						}
					
					}/>

					{uploadingImage && <LinearProgress/>}

					<FormControl>
						<FormLabel id="item-type-radio-buttons-group-label">Item Type</FormLabel>
						<RadioGroup
							aria-labelledby="item-type-radio-buttons-group-label"
							value={editItem?.is_food_item ? "item" : "intermediate"}
							name="item-type"
							onChange={(e) => setEditItem({...editItem!, is_food_item: e.target.value === "item"})}
						>
							<FormControlLabel value="item" control={<Radio />} label="Consumable Item" />
							<FormControlLabel value="intermediate" control={<Radio />} label={<span>Intermediate<br/>(Category, Food State, etc..)</span>} />
						</RadioGroup>
					</FormControl>
					


				</div>
				<div className={"food-image-editor-preview"}>
					{!previewImageUrl && <i>(no image selected)</i>}
					{previewImageUrl && <img className={"preview-image"} src={previewImageUrl}
											 alt={locStr(editItem?.label) ?? ""}/>}

				</div>
			</div>
		</EditorDialog>
			<FoodImageEditor editItem={newFoodImage} setEditItem={setNewFoodImage} onSaveItem={onSaveNewFoodImage}/>
		</>
	)
}