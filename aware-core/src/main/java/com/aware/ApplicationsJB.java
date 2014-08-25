/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware;

/**
 * Service that logs application usage on the device. 
 * Updates every time the user changes application or accesses a sub activity on the screen.
 * - ACTION_AWARE_FOREGROUND_APPLICATION: new application on the screen
 * - ACTION_AWARE_APPLICATIONS: applications running was just updated
 * @author denzil
 */
public class ApplicationsJB extends Applications {
    //Dummy class for backwards compatibility with Jelly Bean and android 2.3.3 accessibility services...
}
