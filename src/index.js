import { NativeModules, NativeEventEmitter } from 'react-native';

const { Cipherlab } = NativeModules;

const events = {};

const eventEmitter = new NativeEventEmitter(Cipherlab);

Cipherlab.on = (event, handler) => {
	const eventListener = eventEmitter.addListener(event, handler);

	events[event] = events[event] ? [...events[event], eventListener] : [eventListener];
};

Cipherlab.off = (event) => {
	if (Object.hasOwnProperty.call(events, event)) {
		const eventListener = events[event].shift();

		if (eventListener) eventListener.remove();
	}
};

Cipherlab.removeAll = (event) => {
	if (Object.hasOwnProperty.call(events, event)) {
		eventEmitter.removeAllListeners(event);

		events[event] = [];
	}
};

export default Cipherlab;
