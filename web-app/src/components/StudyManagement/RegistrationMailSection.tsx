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

import {FC} from "react";
import {StudyManagementSectionProps} from "./interface.ts";
import {Button, Divider, TextField} from "@mui/material";
import {locStr} from "../../utils.ts";
import {EmailConfig, makeNewStudy} from "../../models/study.ts";
import {Role} from "../../models/auth.ts";

export const RegistrationMailSection :FC<StudyManagementSectionProps> = ({study, updateStudy}) => {
    
    const config : EmailConfig = study.email_config;
    
    const validate = (config: EmailConfig) =>
        !!locStr(config.participant_subject).trim() &&
        !!locStr(config.participant_body_template).trim() &&
        !!locStr(config.nurse_subject).trim() &&
        !!locStr(config.nurse_body_template).trim() &&
        !!locStr(config.scientist_body_template).trim() &&
        !!locStr(config.scientist_subject).trim();

    const onUpdateConfig = (partialConfig: Partial<EmailConfig>) => {
        const newConfig = {...config, ...partialConfig};
        updateStudy({...study, email_config: newConfig}, validate(newConfig));
    }

    const onResetMailContent = (target : "participant" | "nurse" | "scientist") => {
        const newStudy = makeNewStudy(locStr(study.title));
        const partial = {
            [`${target}_subject`]: newStudy.email_config[`${target}_subject`],
            [`${target}_body_template`]: newStudy.email_config[`${target}_body_template`],
        };
        onUpdateConfig(partial);
    }
    
    return (
        <>
            <TextField
                label={"Participant Registration E-mail subject*"}
                value={config.participant_subject}
                error={!locStr(config.participant_subject).trim() }
                helperText={!locStr(config.participant_subject).trim() ? "Subject is required" : undefined}
                onChange={e => onUpdateConfig({participant_subject: e.target.value})}
                />
            <div className={"mail-body-wrapper"}>
            <TextField
                label={"Participant Registration Mail Body Template"}
                value={config.participant_body_template}
                error={!locStr(config.participant_body_template).trim()}
                helperText={!locStr(config.participant_body_template).trim()  ? "Body is required" : undefined}
                onChange={e => onUpdateConfig({participant_body_template: e.target.value})}
                multiline={true}
                rows={11}
            />
                <div className={"mail-body-infotext"}>
                <p>The following placeholders should be included:</p>
                <ul>
                    <li><b>%username%</b></li>
                    <li><b>%password%</b></li>
                    <li><b>%nopassword:</b><br/>text displayed if password was not auto-generated%</li>
                    <li><b>%apk_url%</b></li>
                </ul>
                    {/*<Button variant={"contained"} onClick={() => onUpdateConfig({participant_body_template: makeNewStudy(locStr(study.title)).email_config.participant_body_template})}>Reset to default</Button>*/}
                    <Button variant={"contained"} onClick={() => onResetMailContent("participant")}>Reset to default</Button>
                </div>
            </div>

            <Divider/>

            <TextField
                label={"Nurse Registration E-mail subject*"}
                value={config.nurse_subject}
                error={!locStr(config.nurse_subject).trim() }
                helperText={!locStr(config.nurse_subject).trim() ? "Subject is required" : undefined}
                onChange={e => onUpdateConfig({nurse_subject: e.target.value})}
                />
            <div className={"mail-body-wrapper"}>
            <TextField
                label={"Nurse Registration Mail Body Template"}
                value={config.nurse_body_template}
                error={!locStr(config.nurse_body_template).trim()}
                helperText={!locStr(config.nurse_body_template).trim()  ? "Body is required" : undefined}
                onChange={e => onUpdateConfig({nurse_body_template: e.target.value})}
                multiline={true}
                rows={11}
            />
                <div className={"mail-body-infotext"}>
                <p>The following placeholders should be included:</p>
                <ul>
                    <li><b>%username%</b></li>
                    <li><b>%password%</b></li>
                    <li><b>%nopassword:</b><br/>text displayed if password was not auto-generated%</li>
                    <li><b>%apk_url%</b></li>
                </ul>
                    {/*<Button variant={"contained"} onClick={() => onUpdateConfig({nurse_body_template: makeNewStudy(locStr(study.title)).email_config.nurse_body_template})}>Reset to default</Button>*/}
                    <Button variant={"contained"} onClick={() => onResetMailContent("nurse")}>Reset to default</Button>
                </div>
            </div>

            <Divider/>
            
            <TextField
                label={"Scientist Registration E-mail subject*"}
                value={config.scientist_subject}
                error={!locStr(config.scientist_subject).trim() }
                helperText={!locStr(config.scientist_subject).trim() ? "Subject is required" : undefined}
                onChange={e => onUpdateConfig({scientist_subject: e.target.value})}
                />
            <div className={"mail-body-wrapper"}>
            <TextField
                label={"Scientist Registration Mail Body Template"}
                value={config.scientist_body_template}
                error={!locStr(config.scientist_body_template).trim()}
                helperText={!locStr(config.scientist_body_template).trim()  ? "Body is required" : undefined}
                onChange={e => onUpdateConfig({scientist_body_template: e.target.value})}
                multiline={true}
                rows={11}
            />
                <div className={"mail-body-infotext"}>
                <p>The following placeholders should be included:</p>
                <ul>
                    <li><b>%username%</b></li>
                    <li><b>%password%</b></li>
                    <li><b>%nopassword:</b><br/>text displayed if password was not auto-generated%</li>
                    <li><b>%backend_url%</b></li>
                </ul>
                    {/*<Button variant={"contained"} onClick={() => onUpdateConfig({scientist_body_template: makeNewStudy(locStr(study.title)).email_config.scientist_body_template})}>Reset to default</Button>*/}
                    <Button variant={"contained"} onClick={() => onResetMailContent("scientist")}>Reset to default</Button>
                </div>
            </div>
            
            
            
            
        </>
    );
};