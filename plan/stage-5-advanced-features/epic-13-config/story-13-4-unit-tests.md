# Story 13-4: Unit Tests for Config

## Story

As a developer, I want comprehensive unit tests for Config so that all config mechanisms are verified.

## Acceptance Criteria

- [ ] ConfigLoadingTest covers all loading methods
- [ ] ConfigValidationTest covers all validation methods
- [ ] DynamicUpdatesTest covers all update methods
- [ ] Integration test for complete config operations
- [ ] Test coverage > 90%

## Technical Details

### Test Structure

```
src/test/java/org/hyperkv/lsmplus/config/
├── ConfigLoadingTest.java
├── ConfigValidationTest.java
├── DynamicUpdatesTest.java
└── ConfigIntegrationTest.java
```

### Test Cases

1. **ConfigLoadingTest**
   - testLoadFromYaml()
   - testDefaultValues()
   - testAllSettingsLoaded()
   - testInvalidConfig()
   - testMultipleConfigs()

2. **ConfigValidationTest**
   - testValidateValidConfig()
   - testValidateInvalidDataDir()
   - testValidateInvalidTableMaxSize()
   - testValidateInvalidBatchSize()
   - testValidateMultipleErrors()

3. **DynamicUpdatesTest**
   - testUpdateConfig()
   - testListenersNotified()
   - testConfigApplied()
   - testValidationBeforeUpdate()
   - testMultipleUpdates()

4. **ConfigIntegrationTest**
   - testCompleteConfigOperations()
   - testLoadUpdateReload()
   - testConfigUnderLoad()

## Effort Estimate

2 days
