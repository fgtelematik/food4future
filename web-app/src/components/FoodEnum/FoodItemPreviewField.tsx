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

import {Autocomplete, Button, createFilterOptions, InputAdornment, TextField} from "@mui/material";
import {FoodEnumItem} from "../../models/main.ts";
import {locStr} from "../../utils.ts";

import "../FoodImage/FoodImage.css"
import useApi from "../../hooks/useApi";


export const matchAllItem : FoodEnumItem = { label: "Any", identifier: "any", id: "{any}" }

const filterOptions = createFilterOptions<FoodEnumItem>({
	stringify: (option) => locStr(option.label) + " " + (option.identifier ?? "")
});

export const FoodItemPreviewField = (props: {
	label: string,
	value: FoodEnumItem | undefined,
	onChange: (value: FoodEnumItem | undefined) => void,
	items: FoodEnumItem[],
	onAddNew?: () => void,
	errorText?: string,
	useMatchAll?: boolean,
}) => {
	
	const value = props.useMatchAll ? (props.value ?? matchAllItem) : props.value;
	
	return (
		<Autocomplete
			filterOptions={filterOptions}
			disableClearable={true}
			key={props.label}
			options={props.useMatchAll ? [matchAllItem, ...props.items] : props.items}
			getOptionLabel={(item) => locStr(item.label) ?? ""}
			value={value}			
			onChange={(_, value) => props.onChange(props.useMatchAll ? (value == matchAllItem ? undefined : value) : value)}
			isOptionEqualToValue={(option, value) => !value ? false : option.id === value.id}
			renderInput={(params) => (<>

				<TextField
					{...params}
					error={!!props.errorText}
					label={props.label}
					helperText={
					<p>
						{!!props.errorText && <p>{props.errorText}</p>}
						{props.onAddNew && <Button onClick={() => props.onAddNew!()}>Upload new image</Button>}
					</p>
				}
					InputProps={{
						...params.InputProps,
						startAdornment: <InputAdornment position={"start"}>
							{props.value?.image && <img className={"mini-image"} src={useApi().getImageUrl(props.value.image.filename as string)} alt={locStr(props.value?.label) ?? ""}/>}
						</InputAdornment>,
					}}
				/></>
			)}
			renderOption={(props, item, state) => (
				<li {...props} className={"image-dropdown-preview-container" + (state.selected ? " active" : "")} key={item.id}>
					{item.image && <img src={useApi().getImageUrl(item.image.filename as string)} alt={locStr(item.label) ?? ""}/>}
					{
						item === matchAllItem ?
							<p><i>Any (Match All)</i></p> 
							:
							<p>{locStr(item.label)}<br/><span className={"additional-info"}>{item.identifier}</span></p>
					}
				</li>)}
		/>
	)
}