<?xml version="1.0" encoding="UTF-8"?>

<template>
	<head>
	</head>
	<body layout="layer">
		<border template-attach-point="border" template-external="false" template-mutable="false" layout="box">
			<block template-attach-point="text" template-external="false" template-mutable="false" layout="text-edit-layout">
				<template-text template-attach-point="value" template-external="true" template-default="true"
					template-implementation="true" template-multiple="false" multi-line="attributes.multi-line" />
			</block>
		</border>
		<text-cursor-overlay template-attach-point="cursor-overlay" template-external="false" template-mutable="false" />
	</body>
</template>
