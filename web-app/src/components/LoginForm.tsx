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

import {Button, CircularProgress, Paper, TextField} from "@mui/material";
import {useEffect, useState} from "react";
import useApi, {API_URL_LOCAL, API_URL_PRODUCTION, API_URL_TEST} from "../hooks/useApi";
import {AutocompleteEditField} from "./Editor/AutocompleteEditField.tsx";
import {usePersistentState} from "../hooks/useState/usePersistentState.ts";

export const LoginForm = () => {
	const [username, setUsername] = useState("");
	const [password, setPassword] = useState("");
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState(false);
	
	const apiUrl = usePersistentState(state => state.devApiUrl);
	const setApiUrl = (newUrl: string | undefined) =>  newUrl && usePersistentState.setState({devApiUrl: newUrl})
	
	const api = useApi();
	
	useEffect(() => {
		setError(false);
	}, [username, password]);
	
	const onSubmit = async () => {
		setLoading(true);
		try {
			await api.login(username, password);
		} catch(e) {
			console.error(e);
			setError(true);
		} finally {
			setLoading(false);
		}
	}
	
	return (
		<Paper className={"login-form"}>
			
			<h2>Login to f4f server</h2>
			{ error &&
			<p className={"error"}>Login failed</p>
			}
			<form onSubmit={async (e) => {e.preventDefault(); await onSubmit();}}>
			{ loading && <CircularProgress/> || <>
				{ import.meta.env.PROD || <AutocompleteEditField
					freeSolo={true}
					label={"Select API"}
					value={apiUrl}
					onChange={setApiUrl} optionsSource={[API_URL_LOCAL, API_URL_TEST, API_URL_PRODUCTION]}
				/>}
			<TextField
				variant="filled"
				type="text"
				label="Username"
				value={username}
				onChange={event => setUsername(event.target.value)}
			/>
			<TextField
				variant="filled"
				type="password"
				label="Password"
				value={password}
				onChange={event => setPassword(event.target.value)}
			/> </>}
			
				<Button
					disabled={username.length == 0 || password.length == 0 || loading}
					type={"submit"}
					variant="contained"
					onClick={onSubmit}
					>
					Sign In 
				</Button>
				</form>
		</Paper>
	)
}