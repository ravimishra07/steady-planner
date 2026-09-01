import { routes } from './app.routes';

describe('application routes', () => {
  it('provides direct lazy routes for every primary product destination', () => {
    const shell = routes.find((route) => route.path === '');
    const paths = shell?.children?.map((route) => route.path) ?? [];
    expect(paths).toEqual(['today', 'syllabus', 'focus', 'progress', 'settings', 'organise', '']);

    for (const path of ['today', 'syllabus', 'focus', 'progress', 'settings', 'organise']) {
      expect(shell?.children?.find((route) => route.path === path)?.loadComponent).toBeTypeOf('function');
    }
  });

  it('keeps onboarding independently addressable', () => {
    expect(routes.find((route) => route.path === 'onboarding')?.loadComponent).toBeTypeOf('function');
  });
});
