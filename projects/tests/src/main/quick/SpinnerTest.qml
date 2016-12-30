<?xml version="1.0" encoding="UTF-8"?>

<quick>
    <head>
        <title>Spinner Test</title>
		<style-sheet ref="../styles/quick-tests.qss"></style-sheet>
		<model name="model" builder="default-model">
			<variable name="simpleInt" min="0" max="100">0</variable>
			<variable name="simpleFloat" min="-1000" max="1000">0.0</variable>
		</model>
	</head>
	<body xmlns:base="../../../../base/QuickRegistry.xml" layout="box" direction="down">
		<block layout="box" direction="right">
			<spinner value="model.simpleInt" format="${formats.integer}" />
			This spinner increments an integer by 1.
		</block>
		<block layout="box" direction="right">
			<spinner value="model.simpleInt" increment="model.simpleInt+=10" decrement="model.simpleInt-=10" />
			This spinner increments the same integer by 10.
		</block>
		<block layout="box" direction="right">
			<spinner value="model.simpleFloat" increment="model.simpleFloat++" decrement="model.simpleFloat--" />
			This spinner increments a floating-point value by 1.
		</block>
		<block layout="box" direction="right">
			<spinner value="model.simpleFloat" increment="model.simpleFloat+=100" decrement="model.simpleFloat-=100" />
			This spinner increments the same floating-point value by 100.
		</block>
	</body>
</quick>