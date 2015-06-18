profile?=0.98

all: build release

build: js bin

js:
	npm install
	node_modules/.bin/bower install
	node_modules/.bin/gulp
	node_modules/.bin/jsx --extension jsx resources/jsx/ resources/public/js/

bin:
	lein with-profile $(profile) bin

release:
	mkdir -p releases/
	cp -v target/$(profile)/hbase-region-inspector-$(profile)-* releases/
	cp -v target/$(profile)+uberjar/hbase-region-inspector-$(profile)-*.jar releases/

repl:
	lein with-profile $(profile) repl :connect localhost:9999

watch:
	-killall -9 jsx
	node_modules/.bin/jsx --extension jsx --watch resources/jsx/ resources/public/js/ &
	lein with-profile $(profile) ring server-headless

.PHONY: all build bin release js repl watch
