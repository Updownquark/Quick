<?xml version="1.0" encoding="UTF-8"?>

<quick>
    <head>
        <title>Counter Test</title>
        <model name="model" builder="default-model">
	        <model name="counter" builder="counter-model" min="0" max="299" rate="100mi" max-frequency="100mi" />
	        <value name="value">this.counter.value/100.0</value>
        	<switch name="red" value="(int)Math.floor(this.value)">
        		<case value="0">(int) (this.value*255)</case>
        		<case value="1">(int) (255*(2-this.value))</case>
        		<default>0</default>
        	</switch>
        	<switch name="green" value="(int)Math.floor(this.value)">
        		<case value="1">(int) ((this.value-1)*255)</case>
        		<case value="2">(int) (255*(3-this.value))</case>
        		<default>0</default>
        	</switch>
        	<switch name="blue" value="(int)Math.floor(this.value)">
        		<case value="2">(int) ((this.value-2)*255)</case>
        		<case value="0">(int) (255*(1-this.value))</case>
        		<default>0</default>
        	</switch>
        </model>
    </head>
    <body xmlns:base="../../../../base/QuickRegistry.xml" layout="box" direction="down" align="justify">
    	<block layout="box">
	    	Model Value: <label value="model.value" />
	    	Red: <label value="model.red" />
	    	Green: <label value="model.green" />
	    	Blue: <label value="model.blue" />
    	</block>
    	<block layout="box" height="40">
    		<toggle-button selected="model.counter.running">
    			<label value="model.counter.running ? &quot;Pause&quot; : &quot;Resume&quot;" />
    		</toggle-button>
    		<!--<toggle-button selected="model.counter.looping">Looping</toggle-button>-->
    		<button action="model.counter.rate=-model.counter.rate">
    			<label value="model.counter.rate&lt;0 ? &quot;Forward&quot; : &quot;Reverse&quot;" />
   			</button>
    		<button action="model.counter.value=model.counter.min">Reset</button>
    	</block>
    	<block style="bg.transparency=0;bg.color=${rgb(model.red, model.green, model.blue)}" />
    </body>
</quick>
