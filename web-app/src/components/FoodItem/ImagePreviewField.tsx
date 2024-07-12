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

import {Autocomplete, Button, TextField} from "@mui/material";
import {FoodImage} from "../../models/main.ts";
import {locStr} from "../../utils.ts";

import "../FoodImage/FoodImage.css"
import useApi from "../../hooks/useApi";
import {useState} from "react";

export const ImagePreviewField = (props: {
	label: string,
	value: FoodImage | undefined,
	onChange: (value: FoodImage | undefined) => void,
	images: FoodImage[],
	onAddNew?: () => void,
	errorText?: string,
}) => {

	
	return (
		<Autocomplete
			key={props.label}
			options={props.images}
			getOptionLabel={(image) => locStr(image.label) ?? ""}
			value={props.value || null }			
			onChange={(_, value) => props.onChange(value ?? undefined)}
			isOptionEqualToValue={(option, value) => !value ? false : option.id === value.id}
			renderInput={(params) => (
				<TextField
					{...params}
					error={!!props.errorText}
					label={props.label}
					helperText={
					<>
						{!!props.errorText && <span>{props.errorText}</span>}
						{props.onAddNew && <Button onClick={() => props.onAddNew!()}>Upload new image</Button>}
					</>
				}
					InputProps={{
						...params.InputProps,
					}}
				/>
			)}
			renderOption={(props, image, state) => (
				<li {...props} className={"image-dropdown-preview-container" + (state.selected ? " active" : "")} key={image.id}>
						<img src={useApi().getImageUrl(image.filename as string)} alt={locStr(image.label) ?? ""} />
						<p>{locStr(image.label)}</p>
				</li>)}
		/>
	)
}