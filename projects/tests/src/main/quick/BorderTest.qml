<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:base="../../../../base/QuickRegistry.xml" xmlns:test="../../../QuickRegistry.xml">
    <head>
        <title>Testing Borders</title>
    </head>
    <body layout="base:simple">
    	<block layout="base:simple" width="100%" top="0" height="100%">
    		<label layout="base:simple" left="3" right="100%" top="0" height="25%">This is a Quick Document</label>
    		<border left="0" right="100%" top="25%" height="75%">
    			<border style="border-style.color=red;border-style.thickness=5">
	    			<label>This should have a thick little red border and a thin big black border around it</label>
	    		</border>
	    		<block style="bg.transparency=0;bg.color=orange" width="200" height="100">
	    			<border left="0" right="0xp" top="0" bottom="0xp" style="bg.corner-radius=0" layout="simple">
	    				<block left="0" right="0xp" top="0" bottom="0xp" style="bg.transparency=0;bg.color=purple"/>
	    			</border>
	    		</block>
    		</border>
    	</block>
    </body>
</quick>
