profile?=0.98

all: js
	node_modules/.bin/jsx --extension jsx resources/jsx/ resources/public/js/
	lein with-profile $(profile) uberjar
	lein with-profile $(profile) bin

js:
	npm install
	node_modules/.bin/bower install
	node_modules/.bin/gulp

repl:
	lein with-profile $(profile) repl :connect localhost:9999

watch:
	-killall -9 jsx
	node_modules/.bin/jsx --extension jsx --watch resources/jsx/ resources/public/js/ &
	lein with-profile $(profile) ring server-headless

.PHONY: all js repl watch
