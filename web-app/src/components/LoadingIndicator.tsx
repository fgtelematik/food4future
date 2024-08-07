﻿/*
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

import {useDataState} from "../hooks/useState/useData.ts";
import {Grid, LinearProgress} from "@mui/material";

export const LoadingIndicator = () => {
	const isLoading = useDataState(state => state.isLoading);
	
	return (
		<div style={isLoading ? {} : {visibility: "hidden"}}>
		<Grid spacing={1} container>
			<Grid xs item>
				<LinearProgress title="test"/>
			</Grid>
		</Grid>
		</div>
	);
}