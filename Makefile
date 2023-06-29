.PHONY: test-unit

test-unit:
	clj -X:test:runner :dirs '["src/test"]'
