clean:
	rm -rf target

test-unit:
	clj -X:test:runner :dirs '["src/test"]'