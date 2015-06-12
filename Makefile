all: js
	node_modules/.bin/jsx --extension jsx resources/jsx/ resources/public/js/
	lein uberjar

js:
	npm install
	node_modules/.bin/bower install
	node_modules/.bin/gulp

repl:
	lein repl :connect localhost:9999

watch:
	-killall -9 jsx
	node_modules/.bin/jsx --extension jsx --watch resources/jsx/ resources/public/js/ &
	lein ring server-headless

.PHONY: all js repl watch
