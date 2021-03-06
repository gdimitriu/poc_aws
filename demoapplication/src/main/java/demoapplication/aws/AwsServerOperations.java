/*
 Copyright (c) 2019 Gabriel Dimitriu All rights reserved.
 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

 This file is part of poc_aws project.

 poc_aws is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 poc_aws is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with poc_aws.  If not, see <http://www.gnu.org/licenses/>.
 */

package demoapplication.aws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AwsServerOperations {
    /** logger */
    Logger logger = LoggerFactory.getLogger(AwsServerOperations.class);

    public void spawnMachines(Integer number) {
        logger.info("Spawn : " + number);
    }
}
