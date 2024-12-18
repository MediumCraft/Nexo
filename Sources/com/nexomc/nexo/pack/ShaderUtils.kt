package com.nexomc.nexo.pack

import com.nexomc.nexo.configs.Settings
import team.unnamed.creative.base.Writable

class ShaderUtils {
    object ScoreboardBackground {
        fun modernFile(): Writable {
            return Writable.stringUtf8(
                """
                    #version 150
                    
                     in vec3 Position;
                     in vec4 Color;
                    
                     uniform mat4 ModelViewMat;
                     uniform mat4 ProjMat;
                    
                     out vec4 vertexColor;
                    
                     void main() {
                     	gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                    
                     	vertexColor = Color;
                    
                     	//Isolating Scoreboard Display
                     	// Mojang Changed the Z position in 1.21, idk exact value but its huge
                     	if(gl_Position.y > -0.5 && gl_Position.y < 0.85 && gl_Position.x > 0.0 && gl_Position.x <= 1.0 && Position.z > 1000.0 && Position.z < 2750.0) {
                     		//vertexColor = vec4(vec3(0.0,0.0,1.0),1.0); // Debugger
                     		${if (Settings.HIDE_SCOREBOARD_BACKGROUND.toBool()) "vertexColor" else "//SCOREBOARD"}.a = 0.0;
                     	}
                     	else {
                         	//vertexColor = vec4(vec3(1.0,0.0,0.0),1.0);
                     	}
                    
                     	// Uncomment this if you want to make LIST invisible
                     	if(Position.z > 2750.0 && Position.z < 3000.0) {
                     		${if (Settings.HIDE_TABLIST_BACKGROUND.toBool()) "vertexColor" else "//TABLIST"}.a = 0.0;
                     	}
                     }
                    
                    
                    
                    """
                    .trimIndent()
            )
        }
    }
}
