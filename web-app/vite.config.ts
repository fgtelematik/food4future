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

import {defineConfig, loadEnv} from 'vite'
import react from '@vitejs/plugin-react'
import postcssNested from 'postcss-nested';
import VitePluginSsh from 'vite-plugin-ssh/dist'

// https://vitejs.dev/config/
export default defineConfig(({command, mode}) => {
    const env = loadEnv(mode, process.cwd(), '')

    const sshDeployPlugin = VitePluginSsh({
        host: 'f4f.tm.th-wildau.de',
        port: 22,
        username: 'f4f-dev',
        password: env.SSH_DEPLOY_PASSWORD,
        localPath: 'dist',
        remotePath: env.SSH_DEPLOY_PATH || '/opt/f4f-web-app-dev',
    })


    return ({
        plugins: [
            react(),
            env.SSH_DEPLOY_PASSWORD && sshDeployPlugin,],
        css: {
            postcss: {
                plugins: [
                    postcssNested
                ],
            },
        },
    });
})
