profile?=1.0

all: build release

build: js test lint bin

js:
	npm install
	node_modules/.bin/bower install
	node_modules/.bin/gulp
	node_modules/.bin/jsx --extension jsx resources/jsx/ resources/public/js/

bin:
	lein with-profile $(profile) bin

release:
	mkdir -p releases/
	cp -v target/$(profile)/hbase-region-inspector-* releases/

repl:
	lein with-profile $(profile),$(profile)-test repl :connect localhost:9999

lint: cljlint jslint

cljlint:
	lein with-profile $(profile),$(profile)-test eastwood "{:exclude-linters [:deprecations]}"

jslint:
	node_modules/.bin/standard resources/jsx/core.jsx

serve:
	-killall -9 jsx
	node_modules/.bin/jsx --extension jsx --watch resources/jsx/ resources/public/js/ &
	DEBUG=1 lein with-profile $(profile),$(profile)-test,user ring server-headless

test:
	lein with-profile $(profile),$(profile)-test test

autotest:
	# https://github.com/jakemcc/lein-test-refresh/issues/35
	lein with-profile $(profile),$(profile)-test,user test-refresh

doc:
	lein with-profile $(profile) doc

.PHONY: all build bin release js repl lint cljlint jslint serve doc test autotest
