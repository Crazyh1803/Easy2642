import { render } from 'preact';
import { App } from './App';
import { AppStore } from './store/appStore';
import './styles.css';

render(<App store={new AppStore(window.localStorage)} />, document.getElementById('app')!);
